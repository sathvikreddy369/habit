package com.habit1.app.domain.template

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.habit1.app.data.local.db.AppDatabase
import com.habit1.app.data.repository.HabitRepositoryImpl
import com.habit1.app.domain.model.HabitTemplateCategory
import com.habit1.app.domain.validation.MeasurementKind
import com.habit1.app.ui.habits.form.HabitFormViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HabitTemplatesTest {

    @Test
    fun allCategoriesHaveCuratedTemplates() {
        val templates = HabitTemplatesProvider.allTemplates
        assertTrue("Templates catalog must not be empty", templates.isNotEmpty())

        for (category in HabitTemplateCategory.values()) {
            val count = templates.count { it.category == category }
            assertTrue("Category ${category.displayName} must have at least 1 template", count >= 1)
        }
    }

    @Test
    fun filterByCategory_returnsOnlyMatchingTemplates() {
        val engineeringTemplates = HabitTemplatesProvider.filterTemplates(
            category = HabitTemplateCategory.ENGINEERING_STUDENTS,
            searchQuery = ""
        )
        assertTrue(engineeringTemplates.isNotEmpty())
        assertTrue(engineeringTemplates.all { it.category == HabitTemplateCategory.ENGINEERING_STUDENTS })
    }

    @Test
    fun localSearch_isCaseInsensitiveAndMatchesTitleAndDescription() {
        // "water" search should find templates with water in title or description
        val waterTemplates = HabitTemplatesProvider.filterTemplates(
            category = null,
            searchQuery = "WaTeR"
        )
        assertTrue(waterTemplates.isNotEmpty())
        assertTrue(waterTemplates.any { it.title.contains("Water", ignoreCase = true) })

        // "code" or "coding"
        val codeTemplates = HabitTemplatesProvider.filterTemplates(
            category = null,
            searchQuery = "CoDe"
        )
        assertTrue(codeTemplates.isNotEmpty())
    }

    @Test
    fun combinedSearchAndCategoryFiltering_worksCorrectly() {
        val filtered = HabitTemplatesProvider.filterTemplates(
            category = HabitTemplateCategory.ENGINEERING_STUDENTS,
            searchQuery = "LeetCode"
        )
        assertEquals(1, filtered.size)
        assertEquals("LeetCode / Problem Solving", filtered[0].title)
    }

    @Test
    fun emptySearchResults_returnsEmptyList() {
        val results = HabitTemplatesProvider.filterTemplates(
            category = null,
            searchQuery = "NonExistentHabitXYZ987"
        )
        assertTrue(results.isEmpty())
    }

    @Test
    fun habitFormViewModel_prefillsFromTemplateWithoutSaving() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.buildInMemoryDatabase(context)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val mockHabitRepo = HabitRepositoryImpl(database.habitDao(), dispatcher)

        val template = HabitTemplatesProvider.getTemplateById("template_eng_dsa")
        assertNotNull(template)

        val viewModel = HabitFormViewModel(
            habitRepository = mockHabitRepo,
            reminderCoordinator = null,
            initialHabitId = null,
            initialTemplateId = "template_eng_dsa",
            coroutineScope = scope
        )

        val state = viewModel.uiState.value
        assertEquals("DSA Practice", state.name)
        assertEquals(MeasurementKind.COUNT, state.measurementKind)
        assertEquals("2", state.targetInput)
        assertEquals("problems", state.unitInput)
        assertFalse("Selecting template must not mark form as edit mode", state.isEditMode)
        assertFalse("Selecting template must not save habit automatically", state.isSaved)
        assertEquals("No habits should be created in repository until saved", 0, mockHabitRepo.getActiveHabitsList().size)
        database.close()
    }
}
