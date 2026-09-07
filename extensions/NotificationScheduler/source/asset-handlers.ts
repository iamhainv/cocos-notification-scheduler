/*
 NotificationScheduler — Local notification scheduling extension for Cocos Creator 3.8.8
 Android: AlarmManager / NotificationCompat  |  iOS: UNUserNotificationCenter

 MIT License
*/
import { AssetHandlers } from '../@types';

/**
 * @en
 * Texture compression handler for the NotificationScheduler extension.
 *
 * NotificationScheduler does not introduce any custom textures, so this
 * handler is a no-op for all compression tasks.  It exists because
 * `builder.ts` declares `assetHandlers: './asset-handlers'` and the Cocos
 * build pipeline requires the referenced module to export a
 * `compressTextures` function.
 */
export const compressTextures: AssetHandlers.compressTextures = async (tasks) => {
    console.debug(
        `[NotificationScheduler] asset-handlers: compressTextures called with ${tasks.length} task(s) — no-op`,
    );
};
