# jni/

Pont JNI entre Kotlin et la librairie native llama.cpp.

- **LlamaJniService.kt** - Service legacy d'acces JNI (delegue a LlamaApi). Utilise par le code non-Hilt.
- **PredictionEvent.kt** - Sealed class representant les evenements de prediction : Token, Progress, Completion, Error.
