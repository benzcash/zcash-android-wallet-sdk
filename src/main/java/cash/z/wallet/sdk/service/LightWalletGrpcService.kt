package cash.z.wallet.sdk.service

import android.content.Context
import cash.z.wallet.sdk.R
import cash.z.wallet.sdk.exception.LightwalletException
import cash.z.wallet.sdk.ext.ZcashSdk.DEFAULT_LIGHTWALLETD_PORT
import cash.z.wallet.sdk.ext.twig
import cash.z.wallet.sdk.rpc.CompactFormats
import cash.z.wallet.sdk.rpc.CompactTxStreamerGrpc
import cash.z.wallet.sdk.rpc.Service
import com.google.protobuf.ByteString
import io.grpc.Channel
import io.grpc.ManagedChannel
import io.grpc.android.AndroidChannelBuilder
import java.util.concurrent.TimeUnit

/**
 * Implementation of LightwalletService using gRPC for requests to lightwalletd.
 * 
 * @property channel the channel to use for communicating with the lightwalletd server.
 * @property singleRequestTimeoutSec the timeout to use for non-streaming requests. When a new stub is
 * created, it will use a deadline that is after the given duration from now.
 * @property streamingRequestTimeoutSec the timeout to use for streaming requests. When a new stub is
 * created for streaming requests, it will use a deadline that is after the given duration from now.
 */
class LightWalletGrpcService private constructor(
    private val channel: ManagedChannel,
    private val singleRequestTimeoutSec: Long = 10L,
    private val streamingRequestTimeoutSec: Long = 90L
) : LightWalletService {

    /**
     * Construct an instance that corresponds to the given host and port.
     *
     * @param appContext the application context used to check whether TLS is required by this build
     * flavor.
     * @param host the host of the server to use.
     * @param port the port of the server to use.
     * @param usePlaintext whether to use TLS or plaintext for requests. Plaintext is dangerous so
     * it requires jumping through a few more hoops.
     */
    constructor(
        appContext: Context,
        host: String,
        port: Int = DEFAULT_LIGHTWALLETD_PORT,
        usePlaintext: Boolean = appContext.resources.getBoolean(R.bool.lightwalletd_allow_very_insecure_connections)
    ) : this(createDefaultChannel(appContext, host, port, usePlaintext))

    /* LightWalletService implementation */

    override fun getBlockRange(heightRange: IntRange): List<CompactFormats.CompactBlock> {
        channel.resetConnectBackoff()
        return channel.createStub(streamingRequestTimeoutSec).getBlockRange(heightRange.toBlockRange()).toList()
    }

    override fun getLatestBlockHeight(): Int {
        channel.resetConnectBackoff()
        return channel.createStub(singleRequestTimeoutSec).getLatestBlock(Service.ChainSpec.newBuilder().build()).height.toInt()
    }

    override fun submitTransaction(spendTransaction: ByteArray): Service.SendResponse {
        channel.resetConnectBackoff()
        val request = Service.RawTransaction.newBuilder().setData(ByteString.copyFrom(spendTransaction)).build()
        return channel.createStub().sendTransaction(request)
    }

    override fun shutdown() {
        channel.shutdownNow()
    }

    override fun fetchTransaction(txId: ByteArray): Service.RawTransaction? {
        channel.resetConnectBackoff()
        return channel.createStub().getTransaction(Service.TxFilter.newBuilder().setHash(ByteString.copyFrom(txId)).build())
    }


    //
    // Utilities
    //

    private fun Channel.createStub(timeoutSec: Long = 60L): CompactTxStreamerGrpc.CompactTxStreamerBlockingStub =
        CompactTxStreamerGrpc
            .newBlockingStub(this)
            .withDeadlineAfter(timeoutSec, TimeUnit.SECONDS)

    private inline fun Int.toBlockHeight(): Service.BlockID = Service.BlockID.newBuilder().setHeight(this.toLong()).build()

    private inline fun IntRange.toBlockRange(): Service.BlockRange =
        Service.BlockRange.newBuilder()
            .setStart(first.toBlockHeight())
            .setEnd(last.toBlockHeight())
            .build()

    private fun Iterator<CompactFormats.CompactBlock>.toList(): List<CompactFormats.CompactBlock> =
        mutableListOf<CompactFormats.CompactBlock>().apply {
            while (hasNext()) {
                this@apply += next()
            }
        }

    companion object {
        /**
         * Convenience function for creating the default channel to be used for all connections. It
         * is important that this channel can handle transitioning from WiFi to Cellular connections
         * and is properly setup to support TLS, when required.
         */
        fun createDefaultChannel(
            appContext: Context,
            host: String,
            port: Int,
            usePlaintext: Boolean
        ): ManagedChannel {
            twig("Creating connection to $host:$port")
            return AndroidChannelBuilder
                .forAddress(host, port)
                .context(appContext)
                .apply {
                    if (usePlaintext) {
                        if (!appContext.resources.getBoolean(R.bool.lightwalletd_allow_very_insecure_connections)) throw LightwalletException.InsecureConnection
                        usePlaintext()
                    } else {
                        useTransportSecurity()
                    }
                }
                .build()
        }
    }
}