package vn.ggpower.inverter.domain.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vn.ggpower.inverter.domain.home.HomeWidget.Battery
import vn.ggpower.inverter.domain.home.HomeWidget.Power
import vn.ggpower.inverter.domain.home.HomeWidget.Summary
import vn.ggpower.inverter.domain.home.HomeWidget.Yield

class HomeLayoutTest {
    @Test
    fun defaultMatchesPrototype() {
        val l = HomeLayout()
        assertEquals(FlowView.House, l.view)
        assertEquals(listOf(Summary, Power, Yield, Battery), l.widgets)
        assertEquals(emptyList<HomeWidget>(), l.hidden)
        assertEquals(1, l.firstChartIndex)
    }

    @Test
    fun moveReordersAndClamps() {
        val l = HomeLayout()
        assertEquals(listOf(Power, Yield, Summary, Battery), l.move(0, 2).widgets)
        assertEquals(listOf(Battery, Summary, Power, Yield), l.move(3, -5).widgets)
        assertEquals(listOf(Summary, Yield, Battery, Power), l.move(1, 99).widgets)
        assertEquals(l, l.move(7, 0))
    }

    @Test
    fun hideAndShowAppendToEnd() {
        val l = HomeLayout().hide(Power).hide(Summary)
        assertEquals(listOf(Yield, Battery), l.widgets)
        assertEquals(listOf(Summary, Power), l.hidden)
        assertEquals(listOf(Yield, Battery, Summary), l.show(Summary).widgets)
        assertEquals(l.show(Yield), l)
    }

    @Test
    fun firstChartSkipsSummaryAndIsNullWithoutCharts() {
        assertEquals(0, HomeLayout(widgets = listOf(Battery, Summary)).firstChartIndex)
        assertEquals(1, HomeLayout(widgets = listOf(Summary, Yield)).firstChartIndex)
        assertNull(HomeLayout(widgets = listOf(Summary)).firstChartIndex)
        assertNull(HomeLayout(widgets = emptyList()).firstChartIndex)
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val l = HomeLayout(FlowView.Nodes, listOf(Battery, Summary))
        assertEquals(l, HomeLayout.decode(l.view.name, l.encodeWidgets()))
        assertEquals(HomeLayout(FlowView.House, emptyList()), HomeLayout.decode("House", ""))
    }

    @Test
    fun decodeFallsBackToDefaultsAndDropsUnknown() {
        assertEquals(HomeLayout(), HomeLayout.decode(null, null))
        assertEquals(HomeLayout(FlowView.House, listOf(Power, Summary)), HomeLayout.decode("Pie", "pw,xx,sum,pw"))
    }
}
