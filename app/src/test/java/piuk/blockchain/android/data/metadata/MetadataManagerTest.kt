package piuk.blockchain.android.data.metadata

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.wallet.exceptions.InvalidCredentialsException
import info.blockchain.wallet.metadata.Metadata
import info.blockchain.wallet.metadata.MetadataNodeFactory
import io.reactivex.Completable
import io.reactivex.Observable
import org.bitcoinj.crypto.DeterministicKey
import org.junit.Before
import org.junit.Test
import piuk.blockchain.android.RxTest
import piuk.blockchain.android.data.bitcoincash.BchDataManager
import piuk.blockchain.android.data.ethereum.EthDataManager
import piuk.blockchain.android.data.payload.PayloadDataManager
import piuk.blockchain.android.data.rxjava.RxBus
import piuk.blockchain.android.data.shapeshift.ShapeShiftDataManager
import piuk.blockchain.android.util.MetadataUtils
import piuk.blockchain.android.util.StringUtils

@Suppress("IllegalIdentifier")
class MetadataManagerTest : RxTest() {

    private lateinit var subject: MetadataManager
    private val payloadDataManager: PayloadDataManager = mock()
    private val ethDataManager: EthDataManager = mock()
    private val bchDataManager: BchDataManager = mock()
    private val stringUtils: StringUtils = mock()
    private val shapeShiftDataManager: ShapeShiftDataManager = mock()
    private val metadataUtils: MetadataUtils = mock()
    private val rxBus: RxBus = RxBus()

    @Before
    override fun setUp() {
        super.setUp()
        subject = MetadataManager(
                payloadDataManager,
                ethDataManager,
                bchDataManager,
                shapeShiftDataManager,
                stringUtils,
                metadataUtils,
                rxBus
        )
    }

    @Test
    @Throws(Exception::class)
    fun `attemptMetadataSetup load success`() {
        // Arrange
        whenever(payloadDataManager.loadNodes()).thenReturn(Observable.just(true))
        val metadataNodeFactory: MetadataNodeFactory = mock()

        val key: DeterministicKey = mock()
        whenever(payloadDataManager.metadataNodeFactory)
                .thenReturn(Observable.just(metadataNodeFactory))
        whenever(metadataNodeFactory.metadataNode).thenReturn(key)

        whenever(stringUtils.getString(any())).thenReturn("label")
        whenever(ethDataManager.initEthereumWallet(key, "label"))
                .thenReturn(Completable.complete())
        whenever(bchDataManager.initBchWallet(key, "label"))
                .thenReturn(Completable.complete())
        whenever(shapeShiftDataManager.initShapeshiftTradeData(key))
                .thenReturn(Observable.empty())
        // Act
        val testObserver = subject.attemptMetadataSetup().test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        verify(payloadDataManager).loadNodes()
        verify(payloadDataManager).metadataNodeFactory
        verify(ethDataManager).initEthereumWallet(key, "label")
        verify(bchDataManager).initBchWallet(key, "label")
        verify(shapeShiftDataManager).initShapeshiftTradeData(key)
        verifyNoMoreInteractions(payloadDataManager)
    }

    @Test
    @Throws(Exception::class)
    fun `attemptMetadataSetup load fails wo 2nd pw`() {
        // Arrange
        whenever(payloadDataManager.loadNodes()).thenReturn(Observable.just(false))
        whenever(payloadDataManager.isDoubleEncrypted).thenReturn(false)

        val key: DeterministicKey = mock()
        val metadataNodeFactory: MetadataNodeFactory = mock()
        whenever(metadataNodeFactory.metadataNode).thenReturn(key)

        whenever(payloadDataManager.generateAndReturnNodes(null))
                .thenReturn(Observable.just(metadataNodeFactory))
        whenever(stringUtils.getString(any())).thenReturn("label")
        whenever(ethDataManager.initEthereumWallet(key, "label"))
                .thenReturn(Completable.complete())
        whenever(bchDataManager.initBchWallet(key, "label"))
                .thenReturn(Completable.complete())
        whenever(shapeShiftDataManager.initShapeshiftTradeData(key))
                .thenReturn(Observable.empty())
        // Act
        val testObserver = subject.attemptMetadataSetup().test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        verify(payloadDataManager).loadNodes()
        verify(payloadDataManager).generateAndReturnNodes(null)
        verify(payloadDataManager).isDoubleEncrypted
        verify(ethDataManager).initEthereumWallet(key, "label")
        verify(bchDataManager).initBchWallet(key, "label")
        verify(shapeShiftDataManager).initShapeshiftTradeData(key)
    }

    @Test
    @Throws(Exception::class)
    fun `attemptMetadataSetup load fails with 2nd pw`() {
        // Arrange
        whenever(payloadDataManager.loadNodes()).thenReturn(Observable.just(false))
        whenever(payloadDataManager.isDoubleEncrypted).thenReturn(true)
        // Act
        val testObserver = subject.attemptMetadataSetup().test()
        // Assert
        testObserver.assertNotComplete()
        testObserver.assertError(InvalidCredentialsException::class.java)
    }

    @Test
    @Throws(Exception::class)
    fun `generateAndSetupMetadata load success`() {
        // Arrange
        whenever(payloadDataManager.loadNodes()).thenReturn(Observable.just(true))
        val metadataNodeFactory: MetadataNodeFactory = mock()
        val key: DeterministicKey = mock()
        whenever(payloadDataManager.generateNodes(any())).thenReturn(Completable.complete())
        whenever(payloadDataManager.metadataNodeFactory)
                .thenReturn(Observable.just(metadataNodeFactory))
        whenever(metadataNodeFactory.metadataNode).thenReturn(key)

        whenever(stringUtils.getString(any())).thenReturn("label")
        whenever(ethDataManager.initEthereumWallet(key, "label"))
                .thenReturn(Completable.complete())
        whenever(bchDataManager.initBchWallet(key, "label"))
                .thenReturn(Completable.complete())
        whenever(shapeShiftDataManager.initShapeshiftTradeData(key)).thenReturn(Observable.empty())
        // Act
        val testObserver = subject.generateAndSetupMetadata("hello").test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        verify(payloadDataManager).generateNodes("hello")
        verify(payloadDataManager).loadNodes()
        verify(payloadDataManager).metadataNodeFactory
        verify(ethDataManager).initEthereumWallet(key, "label")
        verify(bchDataManager).initBchWallet(key, "label")
        verify(shapeShiftDataManager).initShapeshiftTradeData(key)
        verifyNoMoreInteractions(payloadDataManager)
    }

    @Test
    @Throws(Exception::class)
    fun saveToMetadata() {
        // Arrange
        val type = 1337
        val data = "DATA"
        val factory: MetadataNodeFactory = mock()
        val node: DeterministicKey = mock()
        val metadata: Metadata = mock()
        whenever(payloadDataManager.metadataNodeFactory).thenReturn(Observable.just(factory))
        whenever(factory.metadataNode).thenReturn(node)
        whenever(metadataUtils.getMetadataNode(node, type)).thenReturn(metadata)
        // Act
        val testObserver = subject.saveToMetadata(data, type).test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        verify(payloadDataManager).metadataNodeFactory
        verify(factory).metadataNode
        verify(metadataUtils).getMetadataNode(node, type)
        verify(metadata).putMetadata(data)
    }

}