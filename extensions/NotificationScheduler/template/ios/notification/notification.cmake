# NotificationScheduler extension — iOS CMake build rules.
#
# Mirrors the structure and variable conventions of share.cmake.
# Key differences from share.cmake:
#   * Requires the UserNotifications system framework (linked explicitly).
#   * UIKit is also needed for badge count fallback (iOS < 16) and settings URL.
#   * Pure ObjC — no Swift runtime, no xcframework.
#
# Minimum iOS deployment target: iOS 13.0 (UNUserNotificationCenter APIs used
# here have been available since iOS 10; iOS 13 is the floor for Cocos projects).

# ---------------------------------------------------------------------------
# 1. Collect ObjC bridge sources
# ---------------------------------------------------------------------------

set(NOTIFICATION_PROJ_SOURCES)

list(APPEND NOTIFICATION_PROJ_SOURCES
    ${CMAKE_CURRENT_LIST_DIR}/NotificationServiceHub.mm
    ${CMAKE_CURRENT_LIST_DIR}/NotificationServiceHub.h
)

source_group(TREE ${CMAKE_CURRENT_LIST_DIR} PREFIX "Source Files" FILES ${NOTIFICATION_PROJ_SOURCES})

# Enable ARC on every source file.
foreach(file ${NOTIFICATION_PROJ_SOURCES})
    set_source_files_properties(${file} PROPERTIES COMPILE_OPTIONS "-fobjc-arc")
endforeach()

# ---------------------------------------------------------------------------
# 2. Build the bridge as a static library
# ---------------------------------------------------------------------------

add_library(notification ${NOTIFICATION_PROJ_SOURCES})

target_include_directories(notification PRIVATE
    ${CMAKE_CURRENT_LIST_DIR}
)

# ---------------------------------------------------------------------------
# 3. Link system frameworks
#
# UserNotifications — UNUserNotificationCenter, UNMutableNotificationContent,
#   UNTimeIntervalNotificationTrigger, UNCalendarNotificationTrigger, etc.
# UIKit — UIApplication (badge count fallback, settings URL open).
# ---------------------------------------------------------------------------

target_link_libraries(notification
    "-framework UserNotifications"
    "-framework UIKit"
)

# ---------------------------------------------------------------------------
# 4. Link engine and wire notification into the final executable
# ---------------------------------------------------------------------------

target_link_libraries(notification ${ENGINE_NAME})
target_include_directories(${EXECUTABLE_NAME} PUBLIC ${CMAKE_CURRENT_LIST_DIR})
target_link_libraries(${EXECUTABLE_NAME} notification)

# -ObjC ensures +load and constructor functions inside the static lib run even
# if no symbol is directly referenced by the executable.
target_link_options(${EXECUTABLE_NAME} PRIVATE -ObjC)
