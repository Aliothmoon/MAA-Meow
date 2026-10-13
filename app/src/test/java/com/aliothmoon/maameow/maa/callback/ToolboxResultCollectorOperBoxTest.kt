package com.aliothmoon.maameow.maa.callback

import com.aliothmoon.maameow.data.model.toolbox.OperBoxOperator
import com.aliothmoon.maameow.data.repository.OperBoxRepository
import com.aliothmoon.maameow.data.resource.CharacterInfo
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

/** 对齐上游 #17414：升变形态保留原 ID，未拥有按名字判 */
class ToolboxResultCollectorOperBoxTest {

    private val amiya = CharacterInfo(id = "char_002_amiya", name = "阿米娅", profession = "CASTER", rarity = 5)
    private val amiyaGuard = CharacterInfo(id = "char_1001_amiya2", name = "阿米娅", profession = "WARRIOR", rarity = 5)
    private val amiyaMedic = CharacterInfo(id = "char_1037_amiya3", name = "阿米娅", profession = "MEDIC", rarity = 5)
    private val kroos = CharacterInfo(id = "char_124_kroos", name = "克洛丝", profession = "SNIPER", rarity = 2)

    private val all = listOf(amiya, amiyaGuard, amiyaMedic, kroos).associateBy { it.id }

    /** 花名册不含升变形态，同 ResourceDataManager.operators */
    private val roster = listOf(amiya, kroos).associateBy { it.id }

    private val operBoxRepository = mockk<OperBoxRepository>(relaxed = true)
    private val collector = ToolboxResultCollector(
        resourceDataManager = mockk(relaxed = true) {
            every { operators } returns MutableStateFlow(roster)
            every { getCharacterById(any()) } answers { all[firstArg<String>()] }
        },
        achievementRepository = mockk(relaxed = true),
        depotRepository = mockk(relaxed = true),
        operBoxRepository = operBoxRepository,
    )

    private fun owned(info: CharacterInfo, elite: Int = 2) = OperBoxOperator(
        id = info.id,
        name = info.name,
        rarity = info.rarity,
        elite = elite,
        level = 50,
        potential = 1,
        own = true,
    )

    private fun applied(vararg opers: OperBoxOperator): Pair<List<OperBoxOperator>, List<OperBoxOperator>> {
        val ownedSlot = slot<List<OperBoxOperator>>()
        val notOwnedSlot = slot<List<OperBoxOperator>>()
        collector.applyOperBoxResult(opers.toList())
        verify { operBoxRepository.set(capture(ownedSlot), capture(notOwnedSlot)) }
        return ownedSlot.captured to notOwnedSlot.captured
    }

    @Test
    fun promotedFormsKeepTheirOwnIds() {
        val (ownedList, _) = applied(owned(amiya), owned(amiyaGuard, elite = 1), owned(amiyaMedic, elite = 0))

        assertEquals(
            setOf(amiya.id, amiyaGuard.id, amiyaMedic.id),
            ownedList.map { it.id }.toSet(),
        )
    }

    @Test
    fun ownedPromotedFormCoversBaseFormInNotOwned() {
        // 本地识别只回当前形态
        val (_, notOwned) = applied(owned(amiyaGuard))

        assertEquals(listOf(kroos.id), notOwned.map { it.id })
    }

    @Test
    fun duplicateIdsAreDroppedKeepingFirst() {
        val (ownedList, _) = applied(owned(kroos, elite = 1), owned(kroos, elite = 0))

        assertEquals(listOf(1), ownedList.map { it.elite })
    }
}
