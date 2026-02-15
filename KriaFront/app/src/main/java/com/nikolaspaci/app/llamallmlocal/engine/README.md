# engine/

Abstraction du moteur d'inference LLM. Isole le code JNI du reste de l'application.

- **ModelEngine.kt** - Interface definissant les operations : loadModel, predict, unloadModel, restoreHistory. Expose un `LoadState` (Idle, Loading, Loaded, Error).
- **LlamaEngine.kt** - Implementation singleton avec Mutex pour le thread-safety. Convertit les callbacks JNI en `Flow<PredictionEvent>` via `callbackFlow`.
- **ModelParameterProvider.kt** - Interface + implementation pour fournir les parametres d'un modele depuis la base de donnees.
