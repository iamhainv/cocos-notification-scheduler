# NotificationScheduler extension — iOS pre-build hook.
#
# No .xib or bundle resources to register before the Xcode target is
# configured.  This file exists so the Cocos extension build-hook copy logic
# treats NotificationScheduler identically to AdMob/Share and can include()
# this file unconditionally without a guard.
