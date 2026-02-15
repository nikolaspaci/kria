# ui/chat/

Ecran de chat et ses composants.

- **ChatScreen.kt** - Ecran principal du chat, gere les etats (loading, ready, generating, error)
- **MessageList.kt** - Liste des messages avec scroll automatique et support du streaming
- **MessageRow.kt** - Bulle de message (Markdown pour les reponses bot, texte simple pour l'utilisateur)
- **StreamingMessageBubble.kt** - Bulle animee pendant la generation (curseur clignotant, compteur de tokens, bouton annuler)
- **MarkdownContent.kt** - Rendu Markdown via mikepenz avec style Material 3
- **StreamingMarkdownContent.kt** - Texte brut pendant le streaming, Markdown apres completion
- **CodeBlock.kt** - Bloc de code avec coloration syntaxique, header de langage et bouton copier
- **CopyButton.kt** - Bouton copier dans le presse-papier
- **LoadingScreen.kt** - Indicateur de chargement du modele
- **ErrorScreen.kt** - Affichage d'erreur avec bouton retry
