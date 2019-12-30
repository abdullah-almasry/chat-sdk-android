package co.chatsdk.firefly;

import org.joda.time.DateTime;

import java.util.Date;
import java.util.HashMap;

import co.chatsdk.core.api.APIHelper;
import co.chatsdk.core.dao.DaoCore;
import co.chatsdk.core.dao.Keys;
import co.chatsdk.core.dao.Message;
import co.chatsdk.core.dao.User;
import co.chatsdk.core.events.NetworkEvent;
import co.chatsdk.core.interfaces.ThreadType;
import co.chatsdk.core.session.ChatSDK;
import co.chatsdk.core.types.ConnectionType;
import co.chatsdk.core.types.MessageSendStatus;
import co.chatsdk.core.utils.CrashReportingCompletableObserver;
import co.chatsdk.core.utils.DisposableList;
import co.chatsdk.firebase.FirebaseEventHandler;
import firefly.sdk.chat.chat.Chat;
import firefly.sdk.chat.events.EventType;
import firefly.sdk.chat.firebase.rx.DisposableMap;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import co.chatsdk.core.dao.Thread;
import firefly.sdk.chat.namespace.FireflyMessage;
import firefly.sdk.chat.namespace.Fl;
import firefly.sdk.chat.types.RoleType;

public class FireflyEventHandler extends FirebaseEventHandler implements Consumer<Throwable> {

    protected DisposableMap dm = Fl.y.getDisposableMap();

    @Override
    public void impl_currentUserOn(final String entityID) {
        super.impl_currentUserOn(entityID);
    }

    protected void threadsOn(User chatSDKUser) {

        dm.add(Fl.y.getEvents().getErrors().subscribe(throwable -> {
            throwable.printStackTrace();
        }));

        dm.add(Fl.y.getChatEvents().subscribe(chatEvent -> {
            Chat chat = chatEvent.chat;

            DisposableMap cdm = chat.getDisposableMap();

            if (chatEvent.type == EventType.Added) {

                // Get the thread
                Thread thread = ChatSDK.db().fetchThreadWithEntityID(chat.getId());
                if (thread == null) {
                    thread = DaoCore.getEntityForClass(Thread.class);
                    DaoCore.createEntity(thread);
                    thread.setEntityID(chat.getId());
                    thread.setType(ThreadType.PrivateGroup);
                    thread.setCreationDate(new Date());

                    eventSource.onNext(NetworkEvent.threadAdded(thread));
                }

                final Thread finalThread = thread;

                // TODO: handle name image change
                cdm.add(chat.getNameStream().subscribe(s -> {
                    finalThread.setName(s);
                    eventSource.onNext(NetworkEvent.threadDetailsUpdated(finalThread));
                }));

                cdm.add(chat.getAvatarURLStream().subscribe(s -> {
                    finalThread.setImageUrl(s);
                    eventSource.onNext(NetworkEvent.threadDetailsUpdated(finalThread));
                }));

                cdm.add(chat.getUserEventStream().subscribe(userEvent -> {
                    if (userEvent.type == EventType.Added) {
                        dm.put(chat.getId(), APIHelper.fetchRemoteUser(userEvent.user.id).subscribe(user -> {
                            if (userEvent.getFireflyUser().roleType.equals(RoleType.owner())) {
                                finalThread.setCreator(user);
                            }
                            finalThread.addUser(user);
                            ChatSDK.core().userOn(user).subscribe(new CrashReportingCompletableObserver());
                            eventSource.onNext(NetworkEvent.threadUsersChanged(finalThread, user));
                        }, this));
                    }
                    if (userEvent.type == EventType.Removed) {
                        User user = ChatSDK.db().fetchOrCreateEntityWithEntityID(User.class, userEvent.user.id);
                        finalThread.removeUser(user);
                        eventSource.onNext(NetworkEvent.threadUsersChanged(finalThread, user));
                    }
                }));

                cdm.add(chat.getEvents().getFireflyMessages().subscribe(message -> {
                    cdm.add(handleMessageForThread(message, finalThread));
                }));
            }
        }));

        dm.add(Fl.y.getEvents().getFireflyMessages().subscribe(message -> {

            // Get the user
            dm.add(APIHelper.fetchRemoteUser(message.from).subscribe(user -> {

                // Get the thread
                Thread thread = ChatSDK.db().fetchThreadWithEntityID(message.from);
                if (thread == null) {
                    thread = DaoCore.getEntityForClass(Thread.class);
                    DaoCore.createEntity(thread);
                    thread.setEntityID(message.from);
                    thread.setType(ThreadType.Private1to1);
                    thread.setCreationDate(new Date());
                    thread.setCreator(user);

                    // Add the sender
                    thread.addUsers(user, ChatSDK.currentUser());
                }

                dm.add(handleMessageForThread(message, thread));
            }));
        }));
    }

    protected Disposable handleMessageForThread(FireflyMessage mm, Thread thread) {
        return APIHelper.fetchRemoteUser(mm.from).subscribe(user -> {
            if (!thread.containsMessageWithID(mm.id)) {
                Message message = ChatSDK.db().createEntity(Message.class);

                message.setSender(user);
                message.setMessageStatus(MessageSendStatus.Delivered);
                message.setDate(new DateTime(mm.date));
                message.setEntityID(mm.id);

                HashMap<String, Object> body = mm.getBody();

                Object metaObject = body.get(Keys.Meta);
                if (metaObject instanceof HashMap) {
                    HashMap<String, Object> meta = new HashMap<>((HashMap) metaObject);
                    message.setMetaValues(meta);
                }

                Object typeObject = body.get(Keys.Type);

                if (typeObject instanceof Long) {
                    Integer type = ((Long) typeObject).intValue();
                    message.setType(type);
                }
                if (typeObject instanceof Integer) {
                    Integer type = (Integer) typeObject;
                    message.setType(type);
                }

                thread.addMessage(message);

                eventSource.onNext(NetworkEvent.messageAdded(thread, message));
            }
        }, this);
    }

    @Override
    protected void contactsOn (User chatSDKUser) {
        dm.add(Fl.y.getContactEvents().subscribe(userEvent -> {
            User contact = ChatSDK.db().fetchOrCreateEntityWithEntityID(User.class, userEvent.user.id);
            if (userEvent.type == EventType.Added) {
                dm.add(ChatSDK.contact().addContactLocal(contact, ConnectionType.Contact).doOnError(this).subscribe());
            }
            if (userEvent.type == EventType.Removed) {
                ChatSDK.contact().deleteContactLocal(contact, ConnectionType.Contact);
            }
        }, this));
    }

    @Override
    protected void publicThreadsOn (User user) {
    }

    @Override
    public void impl_currentUserOff(final String entityID) {
        super.impl_currentUserOff(entityID);
    }

    protected void threadsOff (User user) {

    }

    protected void publicThreadsOff (User user) {

    }

    protected void contactsOff (User user) {

    }

    @Override
    public void accept(Throwable throwable) throws Exception {
        throwable.printStackTrace();
    }
}
