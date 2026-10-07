package vn.ggpower.inverter.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * Lưu phiên đăng nhập trên máy (DataStore). Hiện chỉ lưu cờ đã đăng nhập; khi có API thật sẽ lưu token tại đây.
 * Không sao lưu ra ngoài: xem res/xml/data_extraction_rules.xml.
 */
@Singleton
class SessionStore @Inject constructor(@ApplicationContext private val context: Context) {

    val isLoggedIn: Flow<Boolean> = context.sessionDataStore.data.map { it[LOGGED_IN] ?: false }

    suspend fun setLoggedIn(value: Boolean) {
        context.sessionDataStore.edit { it[LOGGED_IN] = value }
    }

    private companion object {
        val LOGGED_IN = booleanPreferencesKey("logged_in")
    }
}
