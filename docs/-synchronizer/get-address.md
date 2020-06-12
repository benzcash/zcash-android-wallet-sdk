[zcash-android-wallet-sdk](../../index.md) / [cash.z.ecc.android.sdk](../index.md) / [Synchronizer](index.md) / [getAddress](./get-address.md)

# getAddress

`abstract suspend fun getAddress(accountId: `[`Int`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)` = 0): `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)

Gets the address for the given account.

### Parameters

`accountId` - the optional accountId whose address is of interest. By default, the first
account is used.

**Return**
the address for the given account.

