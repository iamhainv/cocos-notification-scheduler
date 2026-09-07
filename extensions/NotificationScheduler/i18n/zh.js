module.exports = {
    title: 'Notification Scheduler — 本地通知调度（UNUserNotificationCenter / AlarmManager）',
    description: 'Notification Scheduler — 通过 UNUserNotificationCenter（iOS）和 AlarmManager / NotificationCompat（Android）调度本地通知。',

    enableNotification: {
        title: '启用 Notification Scheduler',
        tip: '启用或禁用 Notification Scheduler 模块。启用后，原生通知集成文件将复制到构建项目中。',
    },

    overwriteLibrary: {
        title: '强制覆盖 Notification Scheduler 库文件',
        tip: '强制将扩展模板中的文件覆盖到构建项目输出目录。',
    },
};
