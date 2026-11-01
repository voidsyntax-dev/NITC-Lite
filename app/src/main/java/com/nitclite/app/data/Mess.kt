package com.nitclite.app.data

import java.time.LocalDate
import java.time.LocalDateTime

data class Meal(val name: String, val start: Int, val end: Int, val items: List<String>, val common: String)

/**
 * Hostel mess menus.
 * - E Mess (pure veg): fixed menu per weekday.
 * - PG2, MBH1-1, MBH1-2, MBH2-1, MBH2-2: one 7-day menu, but each mess is on a different cycle day.
 *   The user tells us which cycle day their mess serves on Sunday; the rest follows.
 */
object Mess {
    const val VEG = "E"
    val names = listOf(VEG, "PG2", "MBH1-1", "MBH1-2", "MBH2-1", "MBH2-2")

    fun label(m: String) = if (m == VEG) "E Mess (Veg)" else "Mess $m"

    private val slots = listOf(
        Triple("Breakfast", 7 * 60, 9 * 60),
        Triple("Lunch", 11 * 60 + 45, 14 * 60),
        Triple("Snacks", 16 * 60, 17 * 60 + 30),
        Triple("Dinner", 19 * 60, 21 * 60),
    )
    private val common = listOf(
        "Tea, Coffee, Milk, Cornflakes/Chocos, Seasonal Fruit, Bread & Jam, Butter/Cheese, Sprouts",
        "Rice, Sambar, Butter Milk, Pickle, Roti, Dal, Pappad/Fryums, Salad",
        "Tea, Coffee, Milk, Lime Juice",
        "Rice, Sambar, Dal, Roti, Butter Milk/Curd, Pappad, Salad",
    )

    private const val ROT_SNACK = "Snacks (daily rotation)"

    /** Cycle day 1..7 → breakfast, lunch, snacks, dinner. Items separated by ';'. */
    private val rotation = listOf(
        listOf(
            "Masala Dosa;Idly, Vada (1 no.);Poha;Chutney, Sambar",
            "Chicken Dry Fry / Fish Fry;Rajma Masala;Aloo Fry;Pachhadi;Sweets / Sliced Fruits",
            ROT_SNACK,
            "Chicken Curry / Aloo Gobi Dry;Green Peas Kuruma;Porotta;Pathiri;Gulab Jamun / Fruits",
        ),
        listOf(
            "Poori, Bhaji;Vellappam, Veg Stew;Oothappam;Chutney, Sambar",
            "Chicken Biriyani / Veg Biriyani;Gobi Manchurian / Podi Paneer;Raita",
            ROT_SNACK,
            "Pulao;Mix Veg Sabji / Soya Fry;Dal Tadka;Puttu;Kadala Curry;Sweets / Fruits",
        ),
        listOf(
            "Rava Dosa;Chutney, Sambar;Pathiri;Green Peas Curry",
            "Chicken Dry Fry / Fish Fry;Ladies Finger Dry Sabji / Aloo Gobi;Kootu Curry;Sweets / Fruits",
            ROT_SNACK,
            "Egg Masala / Egg Burjee;Mutter Paneer / Mix Veg;Vellappam, Sabji",
        ),
        listOf(
            "Idli, Vada;Chutney, Sambar;Noolputtu;Kadala Curry;Aloo Paratha, Curd",
            "Chicken Dry Fry / Fish Fry;Aloo Soybean;Kovakka Fry;Sweets / Fruits",
            ROT_SNACK,
            "Veg Fried Rice;Veg Manchurian;Aloo Mutter Sabji;Sweets / Fruits",
        ),
        listOf(
            "Paav Bhaji;Vellappam, Veg Stew;Millet Upma / Rava Upma",
            "Mandi-Alfam (Peri Peri) / Veg Mandi;Kadai Veg;Veg Mayonnaise;Gulab Jamun / Fruits",
            ROT_SNACK,
            "Chilly Chicken;Paneer Burjee / Capsicum Corn;Nice Pathiri;Sliced Fruits",
        ),
        listOf(
            "Poori, Bhaji;Poha & Namkeen;Puttu, Kadala Curry",
            "Mini Meals;Bisibele Bath, Boondi;Tomato Rice;Chhana Masala;Thoran;Pappadam, Sweet",
            ROT_SNACK,
            "Ghee Rice;Kadai Chicken / Chhole Bhatura;Kadai Paneer;Kheer",
        ),
        listOf(
            "Aloo Paratha / Mix Veg Paratha;Curd;Pongal;Nool Puttu, Kadala Curry",
            "Chicken Biriyani / Egg Biriyani;Paneer Butter Masala;Pickle, Raita",
            ROT_SNACK,
            "Veg Pulao / Jeera Rice;Chilly Gobi;Thattu Dosa;Chutney, Sambar;Veg Soup",
        ),
    )

    /** E Mess, indexed by DayOfWeek.value - 1 (Monday = 0). */
    private val veg = listOf(
        listOf(
            "Masala Dosa;Sambar, Chutney;Poha",
            "Kariyapaku Rice;Veg Chettinadu;Tomato Dal;Kadai Paneer",
            "Veg Samosa;Atukulu;Katta Meetha",
            "Chikkudukaya Masala;Pineapple Rasam;Dum Aloo",
        ),
        listOf(
            "Poori Bhaji;Wheat Upma;Chutney",
            "Tomato Rice;Soy Palak;Turai Ki Dal;Pepper Rasam;Palak Paneer;Curd",
            "Bread Pakoda;Chana Chat;Green Chutney",
            "Aloo Jeera;Bhindi Masala;Dal Khichdi",
        ),
        listOf(
            "Oothappam;Sambar, Chutney;Moong Dal / Besan Chilla",
            "Coconut Rice;Gutti Vankaya;Aloo Gobi Sabji",
            "Veg Manchurian;Bhel Puri;Katta Meetha",
            "Paneer Khurchan;Kadhi Pakoda;Pappu Charu;Aloo Chat Salad",
        ),
        listOf(
            "Idly, Sambar, Chutney;Aloo Paratha;Curd",
            "Pulav Rice;Shahi Paneer;Palak Dal;Kerala Sambar",
            "Peanut Chat;Punugulu;Katta Meetha",
            "Cabbage Thoran;Chole Bhature;Curd Rice",
        ),
        listOf(
            "Set Dosa;Sambar, Chutney;Poha, Namkeen",
            "Jeera Rice;Kothmeer Vankaya;Dal Bhati;Lasun Chutney",
            "Kachori;Maggi;Katta Meetha",
            "Rajma Salad;Veg Kofta Curry;Arvi Pulusu;Kali Dal",
        ),
        listOf(
            "Idly, Vada;Sambar, Chutney",
            "Vankaya Rice;Aloo Vepudu;Chowli Masala;Dal Makhani",
            "Onion Pakoda;Channa Chat;Katta Meetha",
            "Veg Noodles;Chilli Paneer Curry;Tawa Veg",
        ),
        listOf(
            "Paav Bhaji (Toasted);Vermicelli / Rava Upma;Lime, Onion;Boiled Chhana",
            "Veg Pulao;Mushroom Masala;Tomato Rasam;Raita",
            "Vada Pav;Masala Pav;Tomato Sauce",
            "Veg Biriyani;Pindi Chhole;Mirchi Ka Salan;Veg Raita",
        ),
    )

    /** Cycle day (1..7) a rotation mess serves on [date], given the cycle day it serves on Sunday. */
    fun cycleDay(date: LocalDate, sundayCycle: Int): Int = (sundayCycle - 1 + date.dayOfWeek.value % 7) % 7 + 1

    fun day(mess: String, sundayCycle: Int, date: LocalDate): List<Meal> {
        val src = if (mess == VEG) veg[date.dayOfWeek.value - 1] else rotation[cycleDay(date, sundayCycle) - 1]
        return slots.mapIndexed { i, (name, s, e) -> Meal(name, s, e, src[i].split(';'), common[i]) }
    }

    /**
     * The meal to show at [now]: each meal stays up until it ends, then the next one takes over
     * (tomorrow's breakfast after dinner). With [mainOnly], snacks are skipped: lunch runs straight to dinner.
     */
    fun current(mess: String, sundayCycle: Int, now: LocalDateTime, mainOnly: Boolean = false): Pair<LocalDate, Meal> {
        val min = now.hour * 60 + now.minute
        val today = now.toLocalDate()
        fun meals(d: LocalDate) = day(mess, sundayCycle, d).filter { !mainOnly || it.name != "Snacks" }
        meals(today).firstOrNull { min <= it.end }?.let { return today to it }
        val tomorrow = today.plusDays(1)
        return tomorrow to meals(tomorrow).first()
    }
}
