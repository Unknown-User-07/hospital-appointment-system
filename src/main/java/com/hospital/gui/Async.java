package com.hospital.gui;

import javax.swing.SwingWorker;
import java.awt.Component;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/** Runs blocking DB work off the Event Dispatch Thread; results/errors return on the EDT. */
public final class Async {
    private Async() { }

    public static <T> void run(Component parent, Callable<T> work, Consumer<T> onSuccess) {
        run(parent, work, onSuccess, null);
    }

    public static <T> void run(Component parent, Callable<T> work, Consumer<T> onSuccess, Runnable onError) {
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return work.call(); }

            @Override protected void done() {
                try {
                    onSuccess.accept(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Ui.showError(parent, e.getCause());
                    if (onError != null) onError.run();
                }
            }
        }.execute();
    }
}
