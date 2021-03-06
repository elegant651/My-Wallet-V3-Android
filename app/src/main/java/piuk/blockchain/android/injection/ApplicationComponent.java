package piuk.blockchain.android.injection;

import info.blockchain.wallet.util.PrivateKeyFactory;

import javax.inject.Singleton;

import dagger.Component;
import piuk.blockchain.android.BlockchainApplication;
import piuk.blockchain.android.data.contacts.ContactsDataManager;
import piuk.blockchain.android.data.notifications.FcmCallbackService;
import piuk.blockchain.android.data.notifications.InstanceIdService;
import piuk.blockchain.android.ui.base.BaseAuthActivity;
import piuk.blockchain.android.util.AppUtil;
import piuk.blockchain.android.util.ExchangeRateFactory;
import piuk.blockchain.android.util.exceptions.LoggingExceptionHandler;

/**
 * Created by adambennett on 08/08/2016.
 */

@SuppressWarnings("WeakerAccess")
@Singleton
@Component(modules = {
        ApplicationModule.class,
        ApiModule.class,
        PersistentStoreModule.class,
        ServiceModule.class
})
public interface ApplicationComponent {

    // Subcomponent with its own scope
    PresenterComponent plus(DataManagerModule userModule);

    void inject(AppUtil appUtil);

    void inject(LoggingExceptionHandler loggingExceptionHandler);

    void inject(ExchangeRateFactory exchangeRateFactory);

    void inject(PrivateKeyFactory privateKeyFactory);

    void inject(InstanceIdService instanceIdService);

    void inject(BlockchainApplication blockchainApplication);

    void inject(ContactsDataManager contactsDataManager);

    void inject(FcmCallbackService fcmCallbackService);

    void inject(BaseAuthActivity baseAuthActivity);
}
