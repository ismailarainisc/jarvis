package com.raaz.assistant
import com.raaz.assistant.data.*
import org.junit.Assert.*
import org.junit.Test
class ConversationHistoryTest {
 @Test fun existingFormatRemainsReadable(){assertEquals(listOf(Message("user","Hello")),decodeHistory("""[{"role":"user","text":"Hello"}]"""))}
 @Test fun corruptHistoryDoesNotPretendToBeEmpty(){for(raw in listOf("{bad","{}","""[{"role":"unknown","text":"hello"}]""")){assertThrows(HistoryStorageException::class.java){decodeHistory(raw)}}}
 @Test fun oversizedHistoryIsRejectedBeforeParsing(){assertThrows(HistoryStorageException::class.java){decodeHistory(" ".repeat(MAX_HISTORY_CHARS+1))}}
}
