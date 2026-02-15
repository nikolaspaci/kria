# data/database/

Base de donnees Room (version 4).

- **AppDatabase.kt** - Configuration Room avec migrations explicites
- **Conversation.kt** - Entite conversation (titre, apercu, date, nombre de messages)
- **ChatMessage.kt** - Entite message (texte, sender, timestamp, indexes)
- **ChatDao.kt** - Requetes SQL pour conversations et messages, incluant pagination
- **ModelParameter.kt** - Entite parametres du modele (temperature, topK, topP, contextSize, threads, GPU...)
- **ModelParameterDao.kt** - CRUD pour les parametres de modele
- **DatabaseMigrations.kt** - Migrations v2->v3 (metadata conversations) et v3->v4 (parametres etendus)
