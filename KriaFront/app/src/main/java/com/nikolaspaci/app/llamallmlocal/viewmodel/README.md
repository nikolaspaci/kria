# viewmodel/

ViewModels gerant la logique metier et l'etat UI.

- **ChatViewModel.kt** - ViewModel principal du chat (Hilt). Gere le chargement du modele, l'envoi de messages, le streaming, l'annulation et le retry. Expose `ChatUiState` (Idle, ModelLoading, Ready, Generating, MessageComplete, Error).
- **SettingsViewModel.kt** - Gestion des parametres du modele avec validation en temps reel (Hilt)
- **HomeViewModel.kt** - Creation de nouvelles conversations
- **HistoryViewModel.kt** - Liste des conversations passees
- **ModelFileViewModel.kt** - Import et cache des fichiers GGUF
- **ViewModelFactory.kt** - Factory pour les ViewModels non-Hilt (Home, History)
- **ChatViewModelFactory.kt** - Factory legacy (remplace par Hilt + SavedStateHandle)
- **ModelFileViewModelFactory.kt** - Factory pour ModelFileViewModel
