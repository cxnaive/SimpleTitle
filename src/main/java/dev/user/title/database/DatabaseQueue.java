package dev.user.title.database;

import dev.user.title.SimpleTitlePlugin;

import java.sql.SQLException;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 异步数据库操作队列
 * 所有数据库操作提交到队列异步执行，完成后回调到主线程
 */
public class DatabaseQueue {

    private final SimpleTitlePlugin plugin;
    private final BlockingQueue<DatabaseTask<?>> taskQueue;
    private final ExecutorService executor;
    private volatile boolean running = true;

    public DatabaseQueue(SimpleTitlePlugin plugin) {
        this.plugin = plugin;
        this.taskQueue = new LinkedBlockingQueue<>();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "SimpleTitle-DB-Queue");
            t.setDaemon(true);
            return t;
        });

        startProcessing();
    }

    private void startProcessing() {
        executor.submit(() -> {
            while (running || !taskQueue.isEmpty()) {
                try {
                    DatabaseTask<?> task = taskQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (task != null) {
                        processTask(task);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private <T> void processTask(DatabaseTask<T> task) {
        long startTime = System.currentTimeMillis();

        // 使用 try-with-resources 确保连接被正确关闭
        try (java.sql.Connection connection = plugin.getDatabaseManager().getConnection()) {
            T result = task.getOperation().execute(connection);
            long duration = System.currentTimeMillis() - startTime;

            if (duration > 1000) {
                plugin.getLogger().warning("慢查询 [" + task.getName() + "] 耗时: " + duration + "ms");
            }

            // 回调到主线程
            if (task.getCallback() != null) {
                plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                    try {
                        task.getCallback().accept(result);
                    } catch (Exception e) {
                        plugin.getLogger().warning("数据库回调执行失败: " + e.getMessage());
                    }
                });
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("数据库操作失败 [" + task.getName() + "]: " + e.getMessage());

            if (task.getErrorCallback() != null) {
                plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                    task.getErrorCallback().accept(e);
                });
            }
        }
    }

    public <T> void submit(String name, DatabaseOperation<T> operation, Consumer<T> callback, Consumer<SQLException> errorCallback) {
        if (!running) {
            plugin.getLogger().warning("数据库队列已关闭，无法提交任务: " + name);
            return;
        }

        DatabaseTask<T> task = new DatabaseTask<>(name, operation, callback, errorCallback);
        try {
            taskQueue.offer(task, 5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("提交数据库任务被中断: " + name);
        }
    }

    public void submit(String name, DatabaseOperation<Void> operation) {
        submit(name, operation, null, null);
    }

    public void shutdown() {
        running = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    public interface DatabaseOperation<T> {
        T execute(java.sql.Connection connection) throws SQLException;
    }

    private static class DatabaseTask<T> {
        private final String name;
        private final DatabaseOperation<T> operation;
        private final Consumer<T> callback;
        private final Consumer<SQLException> errorCallback;

        public DatabaseTask(String name, DatabaseOperation<T> operation, Consumer<T> callback, Consumer<SQLException> errorCallback) {
            this.name = name;
            this.operation = operation;
            this.callback = callback;
            this.errorCallback = errorCallback;
        }

        public String getName() { return name; }
        public DatabaseOperation<T> getOperation() { return operation; }
        public Consumer<T> getCallback() { return callback; }
        public Consumer<SQLException> getErrorCallback() { return errorCallback; }
    }
}
