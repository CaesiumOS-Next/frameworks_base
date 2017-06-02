LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_PACKAGE_NAME := OverlaysGreen

LOCAL_MODULE_TAGS := tests

LOCAL_CERTIFICATE := platform

LOCAL_USE_AAPT2 := true

include $(BUILD_PACKAGE)
