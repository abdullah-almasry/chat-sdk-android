package co.chatsdk.core.base;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import co.chatsdk.core.dao.User;
import co.chatsdk.core.events.NetworkEvent;
import co.chatsdk.core.handlers.ContactHandler;
import co.chatsdk.core.hook.HookEvent;
import co.chatsdk.core.session.ChatSDK;
import co.chatsdk.core.types.ConnectionType;
import io.reactivex.Completable;
import io.reactivex.CompletableEmitter;
import io.reactivex.CompletableOnSubscribe;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;

/**
 * Created by benjaminsmiley-andrews on 24/05/2017.
 */

public class BaseContactHandler implements ContactHandler {

    @Override
    public List<User> contacts() {
        if (ChatSDK.currentUser() != null) {
            return ChatSDK.currentUser().getContacts();
        }
        return new ArrayList<>();
    }

    @Override
    public boolean exists(User user) {
        for (User u : contacts()) {
            if (u.equalsEntity(user)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<User> contactsWithType(ConnectionType type) {
        if (ChatSDK.currentUser() != null) {
            return ChatSDK.currentUser().getContacts(type);
        }
        return new ArrayList<>();
    }

    @Override
    public Completable addContact(User user, ConnectionType type) {
        addContactLocal(user, type);
        return Completable.complete();
    }

    @Override
    public Completable addContactLocal(User user, ConnectionType type) {
        return Completable.create(emitter -> {
            if (ChatSDK.currentUser() != null && !user.isMe()) {

                ChatSDK.hook().executeHook(HookEvent.ContactWillBeAdded, HookEvent.userData(user));

                ChatSDK.currentUser().addContact(user, type);

                ChatSDK.hook().executeHook(HookEvent.ContactWasAdded, HookEvent.userData(user));
            }
            emitter.onComplete();
        }).concatWith(ChatSDK.core().userOn(user)).doOnComplete(() -> {
            ChatSDK.events().source().onNext(NetworkEvent.contactAdded(user));
        });
    }

    @Override
    public void deleteContactLocal(User user, ConnectionType type) {
        if (ChatSDK.currentUser() != null && !user.isMe()) {

            ChatSDK.hook().executeHook(HookEvent.ContactWillBeDeleted, HookEvent.userData(user));

            ChatSDK.currentUser().deleteContact(user, type);

            ChatSDK.hook().executeHook(HookEvent.ContactWasDeleted, HookEvent.userData(user));
            ChatSDK.events().source().onNext(NetworkEvent.contactDeleted(user));

            ChatSDK.core().userOff(user);
        }
    }

    @Override
    public Completable deleteContact(User user, ConnectionType type) {
        deleteContactLocal(user, type);
        return Completable.complete();
    }

    @Override
    public Completable addContacts(ArrayList<User> users, ConnectionType type) {
        ArrayList<Completable> completables = new ArrayList<>();
        for (User user : users) {
            completables.add(addContact(user, type));
        }
        return Completable.concat(completables);
    }

    @Override
    public Completable deleteContacts(ArrayList<User> users, ConnectionType type) {
        ArrayList<Completable> completables = new ArrayList<>();
        for (User user : users) {
            completables.add(deleteContact(user, type));
        }
        return Completable.concat(completables);
    }

}
