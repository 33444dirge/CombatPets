package su.nightexpress.combatpets.data.impl;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Exercises the same unconditional completion callback used by the supplied NightCore. */
public final class PetSaveVersionCheck {
    public static void main(String[] args) throws Exception {
        completionCannotInterruptRequest();
        PetUser user = new PetUser(UUID.randomUUID(), "SaveCheck", 0, 0, Map.of());
        request(user);
        long older = user.getSaveVersion();
        request(user);
        long newer = user.getSaveVersion();
        user.setCacheFor(0);
        Thread.sleep(5);
        user.acknowledgeSave(older);
        user.disableAutoSave();
        require(user.isAutoSavePlanned(), "old completion must retain newer request");
        require(user.isAutoSaveReady(), "newer request remains eligible for retry");
        require(!user.isCacheExpired(), "unconfirmed data cannot be evicted");
        user.acknowledgeSave(newer);
        user.disableAutoSave();
        require(!user.isAutoSavePlanned(), "confirmed request completes");
        require(user.isCacheExpired(), "confirmed offline cache can expire normally");
        request(user);
        Thread.sleep(5);
        user.disableAutoSave(); // SQL failed: no acknowledgement.
        require(user.isAutoSavePlanned() && user.isAutoSaveReady(), "failed write remains retryable");
        require(!user.isCacheExpired(), "failed write cannot discard cache");
        user.acknowledgeSave(user.getSaveVersion());
        user.acknowledgeSave(older);
        require(!user.isAutoSavePlanned(), "late older acknowledgement cannot undo a confirmed write");
        System.out.println("PetSaveVersionCheck PASS: 11 assertions");
    }

    private static void completionCannotInterruptRequest() throws Exception {
        PetUser user = new PetUser(UUID.randomUUID(), "InterleavingCheck", 0, 0, Map.of());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        Thread callback = new Thread(() -> {
            started.countDown();
            user.disableAutoSave();
            completed.countDown();
        }, "legacy-save-completion");
        synchronized (user) {
            user.setAutoSaveIn(0);
            callback.start();
            require(started.await(2, TimeUnit.SECONDS), "completion thread started");
            require(!completed.await(250, TimeUnit.MILLISECONDS), "completion cannot clear deadline midway through a save request");
            user.markSaveRequested();
        }
        callback.join(2000);
        require(completed.getCount() == 0 && user.isAutoSavePlanned() && user.isAutoSaveReady(),
            "published request remains ready after legacy completion");
    }

    private static void request(PetUser user) {
        user.setAutoSaveIn(0);
        user.markSaveRequested();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
