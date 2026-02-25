package dev.user.title.economy;

import dev.user.title.SimpleTitlePlugin;
import me.yic.xconomy.api.XConomyAPI;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 经济管理器（软依赖 XConomy）
 * 处理金币相关操作
 */
public class EconomyManager {

    private final SimpleTitlePlugin plugin;
    private XConomyAPI xconomyAPI;
    private boolean enabled = false;

    // 异步任务队列
    private final BlockingQueue<EconomyTask<?>> taskQueue;
    private final ExecutorService executor;
    private volatile boolean running = true;

    public EconomyManager(SimpleTitlePlugin plugin) {
        this.plugin = plugin;
        this.taskQueue = new LinkedBlockingQueue<>();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "SimpleTitle-Economy-Queue");
            t.setDaemon(true);
            return t;
        });
    }

    public void init() {
        try {
            xconomyAPI = new XConomyAPI();
            enabled = true;
            plugin.getLogger().info("已连接到 XConomy 经济系统");
            startProcessing();
        } catch (Exception e) {
            plugin.getLogger().info("XConomy 未找到，经济功能不可用: " + e.getMessage());
            enabled = false;
        }
    }

    private void startProcessing() {
        executor.submit(() -> {
            while (running || !taskQueue.isEmpty()) {
                try {
                    EconomyTask<?> task = taskQueue.poll(100, TimeUnit.MILLISECONDS);
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

    @SuppressWarnings("unchecked")
    private <T> void processTask(EconomyTask<T> task) {
        try {
            T result;
            switch (task.getType()) {
                case GET_BALANCE -> result = (T) Double.valueOf(getBalanceSync((Player) task.getPlayer()));
                case HAS_ENOUGH -> result = (T) Boolean.valueOf(hasEnoughSync((Player) task.getPlayer(), (Double) task.getAmount()));
                case WITHDRAW -> result = (T) Boolean.valueOf(withdrawSync((Player) task.getPlayer(), (Double) task.getAmount()));
                case DEPOSIT -> result = (T) Boolean.valueOf(depositSync((Player) task.getPlayer(), (Double) task.getAmount()));
                default -> throw new IllegalStateException("未知任务类型: " + task.getType());
            }

            // 回调到主线程
            if (task.getCallback() != null) {
                plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                    try {
                        ((Consumer<T>) task.getCallback()).accept(result);
                    } catch (Exception e) {
                        plugin.getLogger().warning("经济操作回调执行失败: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            plugin.getLogger().warning("经济操作失败 [" + task.getType() + "]: " + e.getMessage());

            if (task.getErrorCallback() != null) {
                plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                    task.getErrorCallback().accept(e);
                });
            }
        }
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

    public boolean isEnabled() {
        return enabled;
    }

    // ==================== 同步方法（直接调用，供异步线程内部使用） ====================

    private double getBalanceSync(Player player) {
        if (!enabled) return 0;
        try {
            BigDecimal bal = xconomyAPI.getPlayerData(player.getUniqueId()).getBalance();
            return bal.doubleValue();
        } catch (Exception e) {
            plugin.getLogger().warning("获取余额失败: " + e.getMessage());
            return 0;
        }
    }

    private boolean withdrawSync(Player player, double amount) {
        if (!enabled) return false;
        if (amount <= 0) return true;
        try {
            BigDecimal bal = xconomyAPI.getPlayerData(player.getUniqueId()).getBalance();
            if (bal.compareTo(BigDecimal.valueOf(amount)) < 0) {
                return false;
            }
            int result = xconomyAPI.changePlayerBalance(
                player.getUniqueId(),
                player.getName(),
                BigDecimal.valueOf(amount),
                false
            );
            return result == 0;
        } catch (Exception e) {
            plugin.getLogger().warning("扣除金钱失败: " + e.getMessage());
            return false;
        }
    }

    private boolean depositSync(Player player, double amount) {
        if (!enabled) return false;
        if (amount <= 0) return true;
        try {
            int result = xconomyAPI.changePlayerBalance(
                player.getUniqueId(),
                player.getName(),
                BigDecimal.valueOf(amount),
                true
            );
            return result == 0;
        } catch (Exception e) {
            plugin.getLogger().warning("给予金钱失败: " + e.getMessage());
            return false;
        }
    }

    private boolean hasEnoughSync(Player player, double amount) {
        if (!enabled || amount <= 0) return true;
        return getBalanceSync(player) >= amount;
    }

    // ==================== 同步方法（供主线程直接调用） ====================

    public double getBalance(Player player) {
        return getBalanceSync(player);
    }

    public boolean withdraw(Player player, double amount) {
        return withdrawSync(player, amount);
    }

    public boolean deposit(Player player, double amount) {
        return depositSync(player, amount);
    }

    public boolean hasEnough(Player player, double amount) {
        return hasEnoughSync(player, amount);
    }

    // ==================== 异步方法（提交到队列，通过回调返回结果） ====================

    public void getBalanceAsync(Player player, Consumer<Double> callback) {
        getBalanceAsync(player, callback, null);
    }

    public void getBalanceAsync(Player player, Consumer<Double> callback, Consumer<Exception> errorCallback) {
        submitTask(TaskType.GET_BALANCE, player, 0, callback, errorCallback);
    }

    public void withdrawAsync(Player player, double amount, Consumer<Boolean> callback) {
        withdrawAsync(player, amount, callback, null);
    }

    public void withdrawAsync(Player player, double amount, Consumer<Boolean> callback, Consumer<Exception> errorCallback) {
        submitTask(TaskType.WITHDRAW, player, amount, callback, errorCallback);
    }

    public void depositAsync(Player player, double amount, Consumer<Boolean> callback) {
        depositAsync(player, amount, callback, null);
    }

    public void depositAsync(Player player, double amount, Consumer<Boolean> callback, Consumer<Exception> errorCallback) {
        submitTask(TaskType.DEPOSIT, player, amount, callback, errorCallback);
    }

    public void hasEnoughAsync(Player player, double amount, Consumer<Boolean> callback) {
        hasEnoughAsync(player, amount, callback, null);
    }

    public void hasEnoughAsync(Player player, double amount, Consumer<Boolean> callback, Consumer<Exception> errorCallback) {
        submitTask(TaskType.HAS_ENOUGH, player, amount, callback, errorCallback);
    }

    // ==================== 内部辅助方法 ====================

    @SuppressWarnings("unchecked")
    private <T> void submitTask(TaskType type, Player player, double amount, Consumer<T> callback, Consumer<Exception> errorCallback) {
        if (!running) {
            plugin.getLogger().warning("经济队列已关闭，无法提交任务: " + type);
            return;
        }

        EconomyTask<T> task = new EconomyTask<>(type, player, amount, callback, errorCallback);
        try {
            taskQueue.offer(task, 5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("提交经济任务被中断: " + type);
        }
    }

    /**
     * 格式化货币显示
     */
    public String format(double amount) {
        return String.format("%.2f", amount);
    }

    // ==================== 内部类和枚举 ====================

    private enum TaskType {
        GET_BALANCE,
        HAS_ENOUGH,
        WITHDRAW,
        DEPOSIT
    }

    private static class EconomyTask<T> {
        private final TaskType type;
        private final Player player;
        private final double amount;
        private final Consumer<T> callback;
        private final Consumer<Exception> errorCallback;

        public EconomyTask(TaskType type, Player player, double amount, Consumer<T> callback, Consumer<Exception> errorCallback) {
            this.type = type;
            this.player = player;
            this.amount = amount;
            this.callback = callback;
            this.errorCallback = errorCallback;
        }

        public TaskType getType() { return type; }
        public Player getPlayer() { return player; }
        public double getAmount() { return amount; }
        public Consumer<T> getCallback() { return callback; }
        public Consumer<Exception> getErrorCallback() { return errorCallback; }
    }
}
