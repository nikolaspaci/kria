#include "JNIMethods/StopPredictJni.hpp"
#include "session/LlamaSession.hpp"

extern "C" JNIEXPORT void JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_stopPredict(
    JNIEnv *env,
    jobject /* this */,
    jlong session_ptr) {

    LlamaSession* session = reinterpret_cast<LlamaSession*>(session_ptr);
    if (session) {
        session->cancelRequested.store(true);
    }
}
