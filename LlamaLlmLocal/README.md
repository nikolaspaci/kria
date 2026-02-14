# LlamaLlmLocal

Application Android de chat avec des modeles LLM locaux au format GGUF, utilisant llama.cpp via JNI.

## Architecture

Le projet suit le pattern **MVVM** avec **Jetpack Compose** et **Hilt** pour l'injection de dependances.

```
llamallmlocal/
├── LlamaApi.kt              # Declarations JNI natives (init, predict, free, restoreHistory)
├── LlamaApp.kt              # Application Hilt (@HiltAndroidApp)
├── MainActivity.kt           # Point d'entree (@AndroidEntryPoint)
│
├── data/                     # Couche donnees (Room, repositories, validation)
├── di/                       # Modules Hilt
├── engine/                   # Abstraction du moteur LLM
├── jni/                      # Pont JNI Kotlin
├── ui/                       # Interface utilisateur (Compose)
├── usecase/                  # Cas d'utilisation
├── util/                     # Utilitaires (hardware, config)
└── viewmodel/                # ViewModels
```

## Flux principal

1. L'utilisateur selectionne un modele GGUF local
2. `ModelEngine` charge le modele via JNI (`LlamaApi.init`)
3. L'utilisateur envoie un message dans le chat
4. `PredictUseCase` lance la prediction via `ModelEngine.predict`
5. Les tokens sont emis en streaming via `Flow<PredictionEvent>`
6. L'UI affiche les tokens progressivement avec rendu Markdown

## Stack technique

- **UI** : Jetpack Compose + Material 3
- **DI** : Hilt / Dagger
- **Base de donnees** : Room avec migrations explicites
- **Pagination** : Paging 3
- **Markdown** : mikepenz/multiplatform-markdown-renderer
- **Native** : llama.cpp via JNI (C++)
- **Architecture** : MVVM + UseCase
