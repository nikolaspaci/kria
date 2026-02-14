#ifndef HARDWARE_INFO_JNI_HPP
#define HARDWARE_INFO_JNI_HPP

#include <jni.h>

extern "C" {
    JNIEXPORT jboolean JNICALL Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_isVulkanAvailable(JNIEnv *, jobject);
    JNIEXPORT jstring JNICALL Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_getVulkanDeviceInfo(JNIEnv *, jobject);
    JNIEXPORT jint JNICALL Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_getRecommendedGpuLayers(JNIEnv *, jobject);
    JNIEXPORT jlong JNICALL Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_getVulkanVramBytes(JNIEnv *, jobject);
}

#endif // HARDWARE_INFO_JNI_HPP
