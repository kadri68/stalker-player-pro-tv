package com.kadri.stalkerplayerpro.player

/**
 * منظّف احترافي لأسماء الأفلام والمسلسلات قبل البحث عن الترجمة:
 * - يصفّي اسم المسلسل بالكامل من أرقام ورموز المواسم والفصول والحلقات (S01E05, 1x05, Season, Episode, Chapter, الموسم, الفصل, الحلقة, الجزء)
 * - يزيل العلامات المحصورة بين القضبان وعلامات التنصيص والأقواس لجميع اللغات مثل ┃EN┃ ┃NL┃
 * - يزيل بادئات ولواحق السيرفرات والتصنيفات والمنصات والجودات وفرق الرفع
 * - يعالج العناوين المزدوجة (عربي / إنجليزي) والعناوين المركبة ذات النقطتين
 * - يحافظ على سنة الإنتاج في نهاية العنوان
 */
fun cleanMovieTitleForSubtitleSearch(raw: String): String {
    if (raw.isBlank()) return ""
    var s = raw.trim()

    // 1. استخراج سنة الإنتاج وحفظها قبل تنظيف الأقواس والرموز
    val allYears = Regex("""\b(19[2-9]\d|20[0-3]\d)\b""").findAll(s).toList()
    val year = allYears.lastOrNull()?.value.orEmpty()

    // حذف التواريخ الكاملة (YYYY-MM-DD أو DD/MM/YYYY)
    s = s.replace(Regex("""\b(19\d{2}|20\d{2})[-/.]\d{1,2}[-/.]\d{1,2}\b"""), " ")
    s = s.replace(Regex("""\b\d{1,2}[-/.]\d{1,2}[-/.](19\d{2}|20\d{2})\b"""), " ")

    // 2. إزالة العلامات المحصورة بين القضبان العمودية والأقواس المزخرفة لجميع اللغات
    s = s.replace(Regex("""[┃│|¦‖][^┃│|¦‖]{1,24}[┃│|¦‖]"""), " ")
    s = s.replace(Regex("""【[^】]*】|〔[^〕]*〕|《[^》]*》|〈[^〉]*〉|「[^」]*」|『[^』]*』|«[^»]*»"""), " ")
    s = s.replace(Regex("""\[[^\]]*\]|\([^)]*\)|\{[^}]*\}"""), " ")

    // إزالة علامات التنصيص حول رموز اللغات أو التصنيفات القصيرة
    s = s.replace(
        Regex("""(?i)(^|\s)["'“”‘’]\s*([a-z]{2,4}(-[a-z]{2,4})?|multi|dual|sub|dub|vostfr|vf|4k|uhd|fhd|hd|sd|vip|top|new|ar|en|fr|nl|de|es|tr|it|pt|ru|hi|ko|ja)\s*["'“”‘’](?=\s|$)"""),
        " "
    )
    s = s.trim().trim('"', '\'', '“', '”', '‘', '’')

    // 3. إزالة بادئات السيرفرات والمنصات والتصنيفات قبل الفواصل (: | - – — > » /)
    val prefixRegex = Regex(
        """(?i)^\s*(top(\s*\d+)?|new|hot|best|vip|exclusive|premiere|trending|popular|recommended|box\s*office|oscars?|imdb(\s*top)?|""" +
            """netflix|nf|amazon|prime(\s*video)?|disney\+?|(apple\s*)?tv\+?|hbo(\s*max)?|max|hulu|paramount\+?|peacock|starz|showtime|""" +
            """osn|shahid(\s*vip)?|watch\s*it|viu|mbc|bein|rotana|art|a24|marvel|dc|pixar|dreamworks|canal\+?|bbc|itv|""" +
            """4k|8k|uhd|fhd|hd|sd|hdr10\+?|hdr|dv|dolby(\s*vision)?|3d|imax|cam|ts|tc|hdcam|dvd|bluray|blu-ray|web-dl|webrip|""" +
            """ar|ara|arabic|en|eng|english|fr|fre|fra|french|français|nl|nld|dutch|nederlands|de|deu|ger|german|deutsch|""" +
            """es|spa|spanish|español|lat|latino|castellano|it|ita|italian|pt|por|portuguese|pt-br|tr|tur|turkish|türkçe|""" +
            """ru|rus|russian|hi|hin|hindi|ur|urd|urdu|fa|per|farsi|persian|ku|kur|kurdish|ko|kor|korean|ja|jpn|japanese|""" +
            """zh|chi|chinese|pl|pol|polish|ro|ron|romanian|gr|ell|greek|sv|swe|swedish|no|nor|norwegian|da|dan|danish|""" +
            """fi|fin|finnish|cs|cze|czech|hu|hun|hungarian|bg|bul|bulgarian|sr|srp|serbian|hr|hrv|croatian|bs|bosnian|""" +
            """sl|slv|slovenian|mk|macedonian|sq|alb|albanian|uk|ukr|ukrainian|he|heb|hebrew|id|ind|indonesian|ms|malay|""" +
            """th|tha|thai|vi|vie|vietnamese|tl|fil|tagalog|filipino|multi|multisub|dual|vostfr|vf|vff|vfq|truefrench|sub|subs|dub|dubbed|""" +
            """أفلام|افلام|فيلم|فلم|مسلسلات|مسلسل|حصري|حصريا|حصرياً|جديد|جديدة|مترجم|مترجمة|مدبلج|مدبلجة|ترجمة|""" +
            """عربي|عربية|أجنبي|أجنبية|اجنبي|اجنبية|فرنسي|تركي|هندي|كوري|ياباني|إسباني|اسباني|ألماني|الماني|روسي|أنمي|انمي|كرتون|نتفليكس|شاهد|بوكس\s*أوفيس|بوكس\s*اوفيس)""" +
            """\s*[:|┃│¦\-–—>»/]+\s*"""
    )
    repeat(3) {
        s = s.replace(prefixRegex, "")
    }

    // 4. في حال وجود عنوان مزدوج (عربي / إنجليزي) نختار الجزء اللاتيني لدقة البحث
    if (s.contains("/") || s.contains("|")) {
        val parts = s.split(Regex("""\s*[/|┃│]\s*""")).map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size >= 2) {
            val hasArabic = parts.any { it.contains(Regex("""[\u0600-\u06FF]""")) }
            val latinPart = parts.firstOrNull { part ->
                val letters = part.count { it in 'A'..'Z' || it in 'a'..'z' }
                letters >= 3 && !part.contains(Regex("""[\u0600-\u06FF]"""))
            }
            if (hasArabic && latinPart != null) {
                s = latinPart
            }
        }
    }

    // 5. معالجة العناوين المركبة ذات النقطتين مثل "Catching Fire: The Story of Anita Pallenberg 2024"
    if (s.contains(":")) {
        val afterColon = s.substringAfter(":").trim()
        val afterWords = afterColon
            .replace(Regex("""\b(19\d{2}|20\d{2})\b"""), "")
            .trim()
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }
        if (afterWords.size >= 4) {
            s = afterColon
        }
    }

    // 6. تحويل النقاط والشرطات السفلية إلى مسافات وإزالة أسماء فرق الرفع في نهاية الاسم
    s = s.replace(Regex("""-[A-Za-z0-9]{2,14}$"""), " ")
    s = s.replace(Regex("""[._]+"""), " ")

    // 7. تصفية اسم المسلسل بالكامل من أرقام ورموز الفصول والمواسم والحلقات بجميع اللغات
    // الصيغ اللاتينية: S01E05, S01 E05, 1x05, S01, E05, EP05, Season 1, Episode 5, Chapter 2, Part 1, Saison, Bölüm, Temporada...
    s = s.replace(Regex("""(?i)\bS\d{1,2}\s*[-–—]?\s*E\d{1,4}(\s*[-–—]\s*E?\d{1,4})?\b"""), " ")
    s = s.replace(Regex("""(?i)\b\d{1,2}x\d{1,4}\b"""), " ")
    s = s.replace(Regex("""(?i)\b(season|saison|sezon|temporada|staffel|stagione)\s*\d{1,2}\b"""), " ")
    s = s.replace(Regex("""(?i)\b(episode|épisode|episodio|bölüm|bolum|folge|chapter|chapitre|capítulo|capitulo|part|parte|vol|volume)\s*\d{1,4}\b"""), " ")
    s = s.replace(Regex("""(?i)\b(S|E|EP|CH|PT)\s*0*\d{1,4}\b"""), " ")

    // الصيغ العربية: مسلسل، الموسم 1 / الأول..العاشر، الفصل 1 / الأول..العاشر، الحلقة 1 / الأخيرة، الجزء 1، ح 05، م 1، ف 1، ج 1
    s = s.replace(
        Regex("""(^|\s)(مسلسل|الموسم|موسم|الفصل|فصل|الجزء|جزء|الحلقة|حلقة|حلقات)\s*(\d{1,4}|الأول|الاول|الثاني|الثانى|الثالث|الرابع|الخامس|السادس|السابع|الثامن|التاسع|العاشر|الأخيرة|الاخيرة)?(?=\s|$)"""),
        " "
    )
    s = s.replace(Regex("""(^|\s)[حمفج]\s*0*\d{1,4}(?=\s|$)"""), " ")

    // 8. إزالة الجودات والترميزات والنسخ واللغات والعلامات المائية
    s = s.replace(
        Regex(
            """(?i)\b(1080p|720p|480p|360p|2160p|4320p|4k|8k|uhd|fhd|hd|sd|hdr10\+?|hdr|sdr|hlg|dv|dovi|dolby|vision|atmos|""" +
                """bluray|blu-ray|bdrip|brrip|web-dl|webdl|webrip|web|hdrip|dvdrip|dvdscr|hdtv|pdtv|sdtv|camrip|hdcam|cam|ts|tc|telesync|telecine|screener|scr|r5|""" +
                """x264|x265|hevc|avc|av1|vp9|h264|h265|10bit|8bit|aac2?\.?0?|ac3|eac3|ddp5\.?1|ddp2\.?0|dd5\.?1|dd2\.?0|dts-hd|dts-x|dts|truehd|flac|mp3|opus|lpcm|5\.1|7\.1|2\.0|""" +
                """dual|multi|multisub|sub|subs|subbed|dub|dubbed|unsubbed|vf|vff|vfq|vostfr|truefrench|""" +
                """ar|ara|arabic|en|eng|english|fr|fre|fra|french|nl|nld|dutch|de|deu|ger|german|es|spa|spanish|lat|latino|it|ita|italian|pt|por|portuguese|""" +
                """tr|tur|turkish|ru|rus|russian|hi|hin|hindi|ur|urd|urdu|fa|per|farsi|ku|kur|kurdish|ko|kor|korean|ja|jpn|japanese|zh|chi|chinese|""" +
                """pl|pol|polish|ro|ron|romanian|gr|ell|greek|sv|swe|swedish|no|nor|norwegian|da|dan|danish|fi|fin|finnish|cs|cze|czech|hu|hun|hungarian|""" +
                """bg|bul|bulgarian|sr|srp|serbian|hr|hrv|croatian|bs|bosnian|sl|slv|slovenian|mk|macedonian|sq|alb|albanian|uk|ukr|ukrainian|he|heb|hebrew|""" +
                """id|ind|indonesian|ms|malay|th|tha|thai|vi|vie|vietnamese|tl|fil|tagalog|filipino|""" +
                """mp4|mkv|avi|mov|wmv|flv|ts|m2ts|remux|proper|repack|rerip|internal|limited|extended|unrated|theatrical|directors?\s*cut|final\s*cut|imax|open\s*matte|remastered|restored|uncensored|""" +
                """pg-?13|tv-?ma|tv-?14|nc-?17|vip|top|hot|new|best|egybest|cimaclub|shahid4u|mycima|akwam|faselhd|arabseed|movizland|yify|yts|rarbg|psarips|amzn|nf|dsnp|hmax|atvp)\b"""
        ),
        " "
    )

    s = s.replace(
        Regex("""(^|\s)(فيلم|فلم|مترجم|مترجمة|مدبلج|مدبلجة|حصريا|حصرياً|حصري|جودة\s*عالية|كامل|كاملة|اون\s*لاين|أون\s*لاين|نسخة\s*أصلية|نسخة\s*مطولة|نسخة\s*المخرج|بلوراي|ويب\s*ديل|عربي|أجنبي|اجنبي|فرنسي|تركي|هندي|كوري|ياباني|إسباني|اسباني|ألماني|الماني)(\s|$)"""),
        " "
    )

    // إزالة أي سنة مكررة داخل المتن قبل إلحاقها مرة واحدة نظيفة في النهاية
    s = s.replace(Regex("""\b(19[2-9]\d|20[0-3]\d)\b"""), " ")

    // 9. تنظيف الرموز العالقة والمسافات الزائدة
    s = s.replace(Regex("""[|┃│¦‖•★☆◆▪►◄~#*+=<>\[\](){}/\\]+"""), " ")
    s = s.replace(Regex("""\s+[-–—:]+\s+"""), " ")
    s = s.replace(Regex("""\s+"""), " ").trim(' ', '-', '–', '—', ':', '.', '_', '/', '|', '┃', '│')

    return if (year.isNotEmpty() && s.isNotEmpty()) "$s $year" else s.ifEmpty { raw.trim() }
}
