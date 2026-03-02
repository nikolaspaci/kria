#ifndef STOP_PREDICT_JNI_HPP
#define STOP_PREDICT_JNI_HPP
#include <jni.h>

extern "C" JNIEXPORT void JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_stopPredict(JNIEnv *env, jobject /* this */, jlong session_ptr);

#endif // STOP_PREDICT_JNI_HPP
