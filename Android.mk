# Copyright 2008 The Android Open Source Project
#
LOCAL_PATH:= $(call my-dir)

################################################################
include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := $(call all-subdir-java-files)
LOCAL_STATIC_JAVA_LIBRARIES := SVMPProtocol
LOCAL_STATIC_JAVA_LIBRARIES += netty
LOCAL_MODULE := remote_events
include $(BUILD_JAVA_LIBRARY)

################################################################
include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := netty:lib/netty-all-4.0.0.CR4-SNAPSHOT.jar
include $(BUILD_MULTI_PREBUILT)

################################################################
include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := remote_events
LOCAL_MODULE_CLASS := BIN
LOCAL_MODULE_PATH := $(TARGET_OUT)/bin
LOCAL_SRC_FILES := $(LOCAL_MODULE)
include $(BUILD_PREBUILT)

################################################################
include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libremote_events_jni
LOCAL_SRC_FILES:= jni/org_mitre_svmp_events_BaseServer.c
LOCAL_MODULE_PATH := $(TARGET_OUT)/lib
LOCAL_SHARED_LIBRARIES := liblog
include $(BUILD_SHARED_LIBRARY)
