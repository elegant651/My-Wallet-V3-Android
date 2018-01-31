package piuk.blockchain.android.data.bitcoincash

import info.blockchain.api.blockexplorer.BlockExplorer
import info.blockchain.wallet.BitcoinCashWallet
import info.blockchain.wallet.coin.GenericMetadataAccount
import info.blockchain.wallet.coin.GenericMetadataWallet
import info.blockchain.wallet.crypto.DeterministicAccount
import info.blockchain.wallet.multiaddress.TransactionSummary
import info.blockchain.wallet.payload.data.LegacyAddress
import io.reactivex.Completable
import io.reactivex.Observable
import org.bitcoinj.crypto.DeterministicKey
import piuk.blockchain.android.R
import piuk.blockchain.android.data.payload.PayloadDataManager
import piuk.blockchain.android.data.rxjava.RxBus
import piuk.blockchain.android.data.rxjava.RxPinning
import piuk.blockchain.android.data.rxjava.RxUtil
import piuk.blockchain.android.util.MetadataUtils
import piuk.blockchain.android.util.NetworkParameterUtils
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.android.util.annotations.Mockable
import piuk.blockchain.android.util.annotations.WebRequest
import timber.log.Timber
import java.math.BigInteger

@Mockable
class BchDataManager(
        private val payloadDataManager: PayloadDataManager,
        private val bchDataStore: BchDataStore,
        private val metadataUtils: MetadataUtils,
        private val networkParameterUtils: NetworkParameterUtils,
        private val blockExplorer: BlockExplorer,
        private val stringUtils: StringUtils,
        rxBus: RxBus
) {

    private val rxPinning = RxPinning(rxBus)

    /**
     * Clears the currently stored BCH wallet from memory.
     */
    fun clearBchAccountDetails() = bchDataStore.clearBchData()

    /**
     * Fetches EthereumWallet stored in metadata. If metadata entry doesn't exists it will be created.
     *
     * @param defaultLabel The ETH address default label to be used if metadata entry doesn't exist
     * @return An [Completable]
     */
    fun initBchWallet(metadataNode: DeterministicKey, defaultLabel: String) = rxPinning.call {
        Completable.fromCallable {
            fetchOrCreateBchMetadata(metadataNode, defaultLabel)
            restoreBchWallet(bchDataStore.bchMetadata!!)
            return@fromCallable Void.TYPE

        }.compose(RxUtil.applySchedulersToCompletable())
    }

    /**
     * Refreshes bitcoincash metadata. Useful if another platform performed any changes to wallet state.
     * At this point metadataNodeFactory.metadata node will exist.
     */
    fun refreshMetadataCompletable(): Completable =
            Completable.fromObservable(
                    payloadDataManager.metadataNodeFactory
                            .map {
                                initBchWallet(
                                        it.metadataNode,
                                        stringUtils.getString(R.string.bch_default_account_label)
                                )
                            }
            )

    private fun fetchOrCreateBchMetadata(
            metadataKey: DeterministicKey,
            defaultLabel: String
    ) {

        val bchMetadataNode =
                metadataUtils.getMetadataNode(metadataKey, BitcoinCashWallet.METADATA_TYPE_EXTERNAL)
        val walletJson = bchMetadataNode.metadata

        val accountTotal = payloadDataManager.accounts.size

        if (walletJson != null) {
            //Fetch wallet
            bchDataStore.bchMetadata = GenericMetadataWallet.fromJson(walletJson)

            //Sanity check (Add missing metadata accounts)
            bchDataStore.bchMetadata?.accounts?.run {
                val bchAccounts = getMetadataAccounts(defaultLabel, size, accountTotal)
                addAll(bchAccounts)
            }

        } else {
            // Create
            val bchAccounts = getMetadataAccounts(defaultLabel, 0, accountTotal)

            bchDataStore.bchMetadata = GenericMetadataWallet().apply {
                accounts = bchAccounts
                isHasSeen = true
            }
        }
    }

    private fun getMetadataAccounts(
            defaultLabel: String,
            startingAccountIndex: Int,
            accountTotal: Int
    ): ArrayList<GenericMetadataAccount> {
        val bchAccounts = arrayListOf<GenericMetadataAccount>()
        ((startingAccountIndex + 1)..accountTotal)
                .map {
                    return@map when (it) {
                        in 2..accountTotal -> defaultLabel + " " + it
                        else -> defaultLabel
                    }
                }
                .forEach { bchAccounts.add(GenericMetadataAccount(it, false)) }

        return bchAccounts
    }

    /**
     * Restore bitcoin cash wallet
     */
    private fun restoreBchWallet(walletMetadata: GenericMetadataWallet) {
        if (!payloadDataManager.isDoubleEncrypted) {
            bchDataStore.bchWallet = BitcoinCashWallet.restore(
                    blockExplorer,
                    networkParameterUtils.bitcoinCashParams,
                    BitcoinCashWallet.BITCOIN_COIN_PATH,
                    payloadDataManager.mnemonic,
                    ""
            )

            payloadDataManager.accounts.forEachIndexed { i, account ->
                bchDataStore.bchWallet?.addAccount()
                walletMetadata.accounts[i].xpub = account.xpub
            }
        } else {

            bchDataStore.bchWallet = BitcoinCashWallet.createWatchOnly(
                    blockExplorer,
                    networkParameterUtils.bitcoinCashParams
            )

            payloadDataManager.accounts.forEachIndexed { i, account ->
                bchDataStore.bchWallet?.addWatchOnlyAccount(account.xpub)
                walletMetadata.accounts[i].xpub = account.xpub
            }
        }
    }

    fun getActiveXpubs(): List<String> {

        val result = mutableListOf<String>()

        bchDataStore.bchMetadata?.accounts?.forEachIndexed { i, account ->
            if (!account.isArchived) {
                result.add(bchDataStore.bchWallet?.getAccountPubB58(i)!!)
            }
        }

        return result
    }

    fun getActiveXpubsAndImportedAddresses(): List<String> {

        val result = mutableListOf<String>()

        bchDataStore.bchMetadata?.accounts?.forEachIndexed { i, account ->
            if (!account.isArchived) {
                result.add(bchDataStore.bchWallet?.getAccountPubB58(i)!!)
            }
        }

        result.addAll(payloadDataManager.legacyAddressStringList)

        return result
    }

    fun getLegacyAddressStringList() = payloadDataManager.legacyAddressStringList

    fun getWatchOnlyAddressStringList() = payloadDataManager.watchOnlyAddressStringList

    fun updateAllBalances(): Completable {
        val legacyAddresses = payloadDataManager.legacyAddresses
                .filterNot { it.isWatchOnly || it.tag == LegacyAddress.ARCHIVED_ADDRESS }
                .map { it.address }
        val all = getActiveXpubs().plus(legacyAddresses)
        return rxPinning.call { bchDataStore.bchWallet!!.updateAllBalances(legacyAddresses, all) }
                .compose(RxUtil.applySchedulersToCompletable())
    }

    fun getAddressBalance(address: String): BigInteger =
            bchDataStore.bchWallet?.getAddressBalance(address) ?: BigInteger.ZERO

    fun getWalletBalance(): BigInteger =
            bchDataStore.bchWallet?.getWalletBalance() ?: BigInteger.ZERO

    fun getImportedAddressBalance(): BigInteger =
            bchDataStore.bchWallet?.getImportedAddressBalance() ?: BigInteger.ZERO

    fun getAddressTransactions(
            address: String,
            limit: Int,
            offset: Int
    ): Observable<List<TransactionSummary>> =
            rxPinning.call<List<TransactionSummary>> {
                Observable.fromCallable { fetchAddressTransactions(address, limit, offset) }
            }.compose(RxUtil.applySchedulersToObservable())

    fun getWalletTransactions(limit: Int, offset: Int): Observable<List<TransactionSummary>> =
            rxPinning.call<List<TransactionSummary>> {
                Observable.fromCallable { fetchWalletTransactions(limit, offset) }
            }.compose(RxUtil.applySchedulersToObservable())

    fun getImportedAddressTransactions(
            limit: Int,
            offset: Int
    ): Observable<List<TransactionSummary>> =
            rxPinning.call<List<TransactionSummary>> {
                Observable.fromCallable { fetchImportedAddressTransactions(limit, offset) }
            }.compose(RxUtil.applySchedulersToObservable())

    /**
     * Returns all non-archived accounts
     * @return Generic account data that contains label and xpub/address
     */
    fun getActiveAccounts(): List<GenericMetadataAccount> {

        val active = mutableListOf<GenericMetadataAccount>()

        bchDataStore.bchMetadata?.accounts?.forEach {
            if (!it.isArchived) {
                active.add(it)
            }
        }

        return active
    }

    fun getDefaultAccountPosition(): Int = bchDataStore.bchMetadata?.defaultAcccountIdx ?: 0

    fun getDefaultDeterministicAccount(): DeterministicAccount? =
            bchDataStore.bchWallet?.accounts?.get(getDefaultAccountPosition())

    fun getDefaultGenericMetadataAccount(): GenericMetadataAccount? {
        return bchDataStore.bchMetadata?.accounts?.get(getDefaultAccountPosition())
    }

    /**
     * Allows you to generate a BCH receive address at an arbitrary number of positions on the chain
     * from the next valid unused address. For example, the passing 5 as the position will generate
     * an address which correlates with the next available address + 5 positions.
     *
     * @param accountIndex  The index of the [Account] you wish to generate an address from
     * @param addressIndex Represents how many positions on the chain beyond what is already used that
     * you wish to generate
     * @return A Bitcoin Cash receive address in Base58 format
     */
    fun getReceiveAddressAtPosition(accountIndex: Int, addressIndex: Int): String? =
            bchDataStore.bchWallet?.getReceiveAddressAtPosition(accountIndex, addressIndex)

    /**
     * Generates a Base58 Bitcoin Cash receive address for an account at a given position. The
     * address returned will be the next unused in the chain.
     *
     * @param accountIndex The index of the [DeterministicAccount] you wish to generate an address from
     * @return A Bitcoin Cash receive address in Base58 format
     */
    fun getNextReceiveAddress(accountIndex: Int): Observable<String> =
            Observable.fromCallable {
                bchDataStore.bchWallet!!.getNextReceiveAddress(accountIndex)
            }

    /**
     * Generates a Base58 Bitcoin Cash change address for an account at a given position. The
     * address returned will be the next unused in the chain.
     *
     * @param accountIndex The index of the [DeterministicAccount] you wish to generate an address from
     * @return A Bitcoin Cash change address in Base58 format
     */
    fun getNextChangeAddress(accountIndex: Int): Observable<String> =
            Observable.fromCallable {
                bchDataStore.bchWallet!!.getNextChangeAddress(accountIndex)
            }

    /**
     * Allows you to generate a BCH change address at an arbitrary number of positions on the chain
     * from the next valid unused address. For example, the passing 5 as the position will generate
     * an address which correlates with the next available address + 5 positions.
     *
     * @param accountIndex  The index of the [Account] you wish to generate an address from
     * @param addressIndex Represents how many positions on the chain beyond what is already used that
     * you wish to generate
     * @return A Bitcoin Cash change address in Base58 format
     */
    fun getChangeAddressAtPosition(accountIndex: Int, addressIndex: Int): Observable<String> =
            Observable.fromCallable {
                bchDataStore.bchWallet!!.getChangeAddressAtPosition(accountIndex, addressIndex)
            }

    fun incrementNextReceiveAddressBch(xpub: String): Completable =
            Completable.fromCallable {
                bchDataStore.bchWallet!!.incrementNextReceiveAddressBch(xpub)
            }

    fun incrementNextChangeAddressBch(xpub: String): Completable =
            Completable.fromCallable {
                bchDataStore.bchWallet!!.incrementNextChangeAddressBch(xpub)
            }

    fun isOwnAddress(address: String) =
            bchDataStore.bchWallet?.isOwnAddress(address) ?: false

    /**
     * Converts any Bitcoin Cash address to a label.
     *
     * @param address Accepts account receive or change chain address, as well as legacy address.
     * @return Account or legacy address label
     */
    fun getLabelFromBchAddress(address: String): String? {
        val xpub = bchDataStore.bchWallet?.getXpubFromAddress(address)

        return bchDataStore.bchMetadata?.accounts?.find { it.xpub == xpub }?.label
    }

    ///////////////////////////////////////////////////////////////////////////
    // Web requests that require wrapping in Observables
    ///////////////////////////////////////////////////////////////////////////

    @WebRequest
    private fun fetchAddressTransactions(
            address: String,
            limit: Int,
            offset: Int
    ): MutableList<TransactionSummary> =
            bchDataStore.bchWallet!!.getTransactions(
                    null,//legacy list
                    mutableListOf(),//watch-only list
                    getActiveXpubsAndImportedAddresses(),
                    address,
                    limit,
                    offset
            )

    @WebRequest
    private fun fetchWalletTransactions(limit: Int, offset: Int): MutableList<TransactionSummary> =
            bchDataStore.bchWallet!!.getTransactions(
                    null,//legacy list
                    mutableListOf(),//watch-only list
                    getActiveXpubsAndImportedAddresses(),
                    null,
                    limit,
                    offset
            )

    @WebRequest
    private fun fetchImportedAddressTransactions(
            limit: Int,
            offset: Int
    ): MutableList<TransactionSummary> =
            bchDataStore.bchWallet!!.getTransactions(
                    payloadDataManager.legacyAddressStringList,//legacy list
                    mutableListOf(),//watch-only list
                    getActiveXpubsAndImportedAddresses(),
                    null,
                    limit,
                    offset
            )

    fun getXpubFromAddress(address: String) =
        bchDataStore.bchWallet!!.getXpubFromAddress(address)
}