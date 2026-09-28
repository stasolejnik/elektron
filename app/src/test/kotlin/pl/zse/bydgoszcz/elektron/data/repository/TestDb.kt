package pl.zse.bydgoszcz.elektron.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import pl.zse.bydgoszcz.elektron.data.local.AppDatabase

/** Świeża baza w pamięci dla każdego testu (prawdziwy SQLite przez Robolectric). */
fun inMemoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
