package id.game.lariketik

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import kotlin.math.*
import kotlin.random.Random

/**
 * LARI KETIK: BAYANGAN MALAM
 * Game 2D Side-Scrolling Typing Runner Native Android (Kotlin)
 *
 * Fitur Utama:
 * - Genre 2D Side-Scroll Runner dengan latar malam mencekam & siluet pohon beringin/bambu.
 * - Dikejar bayangan monster Urban Legend Indonesia (Genderuwo, Kuntilanak, Banaspati, Wewe Gombel).
 * - Karakter berlari di atas pijakan huruf dengan mengetik kata utuh Bahasa Indonesia
 *   menggunakan Keyboard Virtual QWERTY di bagian bawah layar.
 * - Salah ketik (typo) membuat karakter melambat sehingga bayangan monster mendekat.
 * - Jika salah ketik lebih dari 5 kali (typo ke-6), karakter langsung tersandung & jatuh!
 */
class MainActivity : Activity() {

    private lateinit var gameView: TypingRunnerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        gameView = TypingRunnerView(this)
        setContentView(gameView)
    }

    override fun onPause() {
        super.onPause()
        gameView.pauseGame()
    }

    override fun onResume() {
        super.onResume()
        gameView.resumeGame()
    }
}

class TypingRunnerView(context: Context) : View(context) {

    // Status permainan
    private enum class GameState {
        MENU, PLAYING, TRIPPED_FALLING, GAME_OVER
    }

    private var state = GameState.MENU
    private var gameOverReason = ""

    // Bank kata Bahasa Indonesia bertema malam, lari, dan mitos Nusantara
    private val wordBankEasy = listOf(
        "LARI", "KABUR", "MALAM", "GELAP", "KABUT", "HANTU", "CEPAT", "JALAN",
        "TAKUT", "SUARA", "ANGIN", "POHON", "SEMAK", "BATU", "TANAH", "JEJAK",
        "NAPAS", "SAKSI", "MITOS", "NYALI", "SENJA", "BULAN", "DARAH", "bisik".uppercase(),
        "kaki".uppercase(), "mata".uppercase(), "asap".uppercase(), "bara".uppercase()
    )

    private val wordBankMedium = listOf(
        "BAYANGAN", "MERINDING", "JEMBATAN", "BERINGIN", "KUBURAN", "LENTERA",
        "LANGKAH", "TERIAKAN", "MISTERI", "KERAMAT", "PENUNGGU", "ANCAMAN",
        "LONCATAN", "SETAPAK", "GEMETAR", "KEJARAN", "SERANGAN", "BENTENG",
        "PANTANGAN", "SUMPAHAN", "GERBANG", "LORONG", "TENGAH", "SELAMAT"
    )

    private val wordBankHard = listOf(
        "GENDERUWO", "KUNTILANAK", "BANASPATI", "WEWEGOMBEL", "SILUMAN",
        "KEGELAPAN", "PERSEMBUNYIAN", "KESELAMATAN", "PENAMPAKAN", "MENCEKAM",
        "TERSANDUNG", "MELarikan".uppercase(), "PERJALANAN", "BERSEMBUNYI", "MEMBAYANGI"
    )

    // Daftar sosok Urban Legend pengejar
    private val monsterNames = listOf(
        "Bayangan Genderuwo",
        "Siluet Kuntilanak",
        "Kabut Banaspati",
        "Sosok Wewe Gombel"
    )
    private var currentMonsterIndex = 0

    // Kata aktif & antrean kata berikutnya di jalan
    private var currentWord = "LARI"
    private var nextWord = "KABUR"
    private var typedIndex = 0

    // Statistik & mekanik kecepatan / typo
    private var score = 0
    private var wordsCompleted = 0
    private var distanceRunMeters = 0f
    private var monsterDistance = 38f // Meter di belakang karakter (0 = tertangkap)
    private val maxMonsterDistance = 65f

    private var playerSpeed = 10f
    private val baseTargetSpeed = 11f
    private var slowdownTimer = 0f

    // Mekanik Salah Ketik: > 5x (yaitu 6x) membuat tersandung & jatuh
    private var typoCount = 0
    private val maxAllowedTypos = 5
    private var typoFlashAlpha = 0f
    private var tripAnimationProgress = 0f // 0..1 saat karakter tersandung jatuh

    // Animasi dunia & parallax
    private var worldScrollX = 0f
    private var runnerAnimPhase = 0f
    private var monsterAnimPhase = 0f
    private var lastFrameTime = System.nanoTime()
    private var isRunning = true

    // Efek partikel langkah & percikan huruf
    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var radius: Float,
        var color: Int,
        var alpha: Int = 255
    )
    private val particles = mutableListOf<Particle>()

    // Struktur tombol Keyboard Virtual QWERTY
    private data class KeyButton(
        val char: Char,
        val rect: RectF = RectF(),
        var pressAnim: Float = 0f
    )

    private val keyboardRows = listOf(
        "QWERTYUIOP",
        "ASDFGHJKL",
        "ZXCVBNM"
    )
    private val keyButtons = mutableListOf<KeyButton>()
    private val startButtonRect = RectF()

    // Paint untuk rendering grafis 2D
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    // Audio sintetis ringan & getar
    private var toneGenerator: ToneGenerator? = null
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 65)
        } catch (_: Exception) {
        }
        for (row in keyboardRows) {
            for (c in row) {
                keyButtons.add(KeyButton(c))
            }
        }
    }

    fun pauseGame() {
        isRunning = false
    }

    fun resumeGame() {
        isRunning = true
        lastFrameTime = System.nanoTime()
        invalidate()
    }

    private fun startNewGame() {
        score = 0
        wordsCompleted = 0
        distanceRunMeters = 0f
        monsterDistance = 38f
        playerSpeed = baseTargetSpeed
        slowdownTimer = 0f
        typoCount = 0
        typoFlashAlpha = 0f
        tripAnimationProgress = 0f
        currentMonsterIndex = 0
        particles.clear()

        currentWord = pickNextWord()
        nextWord = pickNextWord()
        typedIndex = 0
        state = GameState.PLAYING
        lastFrameTime = System.nanoTime()
        invalidate()
    }

    private fun pickNextWord(): String {
        val pool = when {
            wordsCompleted < 5 -> wordBankEasy
            wordsCompleted < 14 -> wordBankEasy + wordBankMedium
            else -> wordBankMedium + wordBankHard
        }
        var candidate = pool[Random.nextInt(pool.size)]
        if (candidate == currentWord && pool.size > 1) {
            candidate = pool[(pool.indexOf(candidate) + 1) % pool.size]
        }
        return candidate
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutVirtualKeyboard(w.toFloat(), h.toFloat())
    }

    private fun layoutVirtualKeyboard(w: Float, h: Float) {
        val kbTop = h * 0.66f
        val kbHeight = h * 0.31f
        val rowHeight = kbHeight / 3f
        val pad = w * 0.008f

        var index = 0
        for (rowIdx in keyboardRows.indices) {
            val rowStr = keyboardRows[rowIdx]
            val count = rowStr.length
            val maxCols = 10f
            val keyW = (w * 0.96f) / maxCols
            val rowWidth = count * keyW
            val startX = (w - rowWidth) / 2f
            val top = kbTop + rowIdx * rowHeight

            for (colIdx in 0 until count) {
                val btn = keyButtons[index++]
                btn.rect.set(
                    startX + colIdx * keyW + pad,
                    top + pad * 1.2f,
                    startX + (colIdx + 1) * keyW - pad,
                    top + rowHeight - pad * 1.2f
                )
            }
        }

        startButtonRect.set(w * 0.2f, h * 0.68f, w * 0.8f, h * 0.78f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        val dt = ((now - lastFrameTime) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastFrameTime = now

        if (state == GameState.PLAYING || state == GameState.TRIPPED_FALLING) {
            updateGame(dt)
        } else {
            // Animasi idle di menu/gameover
            monsterAnimPhase += dt * 3f
            worldScrollX += dt * 35f
        }

        val w = width.toFloat()
        val h = height.toFloat()
        val groundY = h * 0.54f

        // 1. Gambar langit malam & bulan merah/pucat
        drawNightBackground(canvas, w, h, groundY)

        // 2. Gambar jalan pijakan huruf (Word Bridge)
        drawLetterBridge(canvas, w, h, groundY)

        // 3. Gambar Bayangan Monster Urban Legend di kiri
        drawUrbanLegendShadow(canvas, w, groundY)

        // 4. Gambar Karakter Utama (Pelari)
        drawRunnerCharacter(canvas, w, groundY)

        // 5. Gambar partikel
        drawParticles(canvas)

        // 6. Gambar HUD informasi skor, jarak monster, kata aktif, dan indikator salah ketik
        drawHud(canvas, w, h)

        // 7. Gambar Keyboard Virtual di bagian bawah
        drawVirtualKeyboard(canvas, w, h)

        // 8. Efek layar merah saat salah ketik
        if (typoFlashAlpha > 0f) {
            shapePaint.color = Color.argb((typoFlashAlpha * 130).toInt().coerceIn(0, 160), 220, 20, 30)
            canvas.drawRect(0f, 0f, w, h, shapePaint)
        }

        // 9. Overlay Menu atau Game Over
        if (state == GameState.MENU || state == GameState.GAME_OVER) {
            drawOverlayScreen(canvas, w, h)
        }

        if (isRunning) {
            postInvalidateOnAnimation()
        }
    }

    private fun updateGame(dt: Float) {
        // Update animasi tombol keyboard
        for (btn in keyButtons) {
            if (btn.pressAnim > 0f) {
                btn.pressAnim = (btn.pressAnim - dt * 5f).coerceAtLeast(0f)
            }
        }
        if (typoFlashAlpha > 0f) {
            typoFlashAlpha = (typoFlashAlpha - dt * 2.5f).coerceAtLeast(0f)
        }

        if (state == GameState.TRIPPED_FALLING) {
            // Karakter sedang tersandung jatuh akibat >5x typo
            tripAnimationProgress = (tripAnimationProgress + dt * 1.35f).coerceAtMost(1f)
            playerSpeed = (playerSpeed - dt * 18f).coerceAtLeast(0f)
            worldScrollX += playerSpeed * dt * 24f
            monsterAnimPhase += dt * 8f
            // Bayangan monster langsung menerkam saat karakter jatuh
            monsterDistance = (monsterDistance - dt * 32f).coerceAtLeast(0f)

            if (tripAnimationProgress >= 1f && monsterDistance <= 0.5f) {
                state = GameState.GAME_OVER
                gameOverReason = "Kamu tersandung & jatuh karena salah ketik lebih dari 5 kali!"
            }
            updateParticles(dt)
            return
        }

        // Mode PLAYING normal
        if (slowdownTimer > 0f) {
            slowdownTimer = (slowdownTimer - dt).coerceAtLeast(0f)
        }

        // Kecepatan alami melambat sedikit demi sedikit jika pemain berhenti mengetik
        val difficultyBonus = (wordsCompleted * 0.18f).coerceAtMost(4.5f)
        val minPassiveSpeed = if (slowdownTimer > 0f) 3.2f else 6.0f
        playerSpeed = (playerSpeed - dt * 2.4f).coerceAtLeast(minPassiveSpeed)

        // Kecepatan bayangan monster mengejar
        val monsterSpeed = baseTargetSpeed + difficultyBonus * 0.85f
        val relativeDelta = (playerSpeed - monsterSpeed) * dt * 1.45f
        monsterDistance = (monsterDistance + relativeDelta).coerceIn(0f, maxMonsterDistance)

        // Scroll dunia & animasi kaki karakter
        worldScrollX += playerSpeed * dt * 32f
        runnerAnimPhase += dt * (playerSpeed * 1.35f)
        monsterAnimPhase += dt * 5.5f
        distanceRunMeters += playerSpeed * dt * 0.8f

        // Cek apakah tertangkap bayangan monster
        if (monsterDistance <= 0f) {
            state = GameState.GAME_OVER
            val monsterName = monsterNames[currentMonsterIndex]
            gameOverReason = "Tertangkap oleh $monsterName! Ketik lebih cepat & hindari salah ketik."
            vibratePhone(250)
        }

        updateParticles(dt)
    }

    private fun updateParticles(dt: Float) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vy += 400f * dt
            p.alpha -= (dt * 380f).toInt()
            if (p.alpha <= 0) iterator.remove()
        }
    }

    private fun handleKeyInput(charTyped: Char) {
        if (state != GameState.PLAYING) return

        val expectedChar = currentWord[typedIndex]
        if (charTyped == expectedChar) {
            // HURUF BENAR!
            typedIndex++
            score += 15
            // Tambah kecepatan lari
            playerSpeed = (playerSpeed + 2.4f).coerceAtMost(18.5f)
            monsterDistance = (monsterDistance + 1.35f).coerceAtMost(maxMonsterDistance)
            playTone(ToneGenerator.TONE_PROP_BEEP, 35)
            spawnStepParticles(true)

            // Cek apakah satu kata utuh selesai diketik
            if (typedIndex >= currentWord.length) {
                score += currentWord.length * 25
                wordsCompleted++
                // Bonus dorongan lari saat menyelesaikan kata utuh
                playerSpeed = (playerSpeed + 3.0f).coerceAtMost(20f)
                monsterDistance = (monsterDistance + 3.2f).coerceAtMost(maxMonsterDistance)

                // Ganti jenis monster urban legend setiap 5 kata
                currentMonsterIndex = (wordsCompleted / 5) % monsterNames.size

                currentWord = nextWord
                nextWord = pickNextWord()
                typedIndex = 0
                playTone(ToneGenerator.TONE_PROP_ACK, 70)
            }
        } else {
            // SALAH KETIK (TYPO)!
            typoCount++
            typoFlashAlpha = 1f
            slowdownTimer = 1.6f

            // Karakter langsung melambat dan monster mendekat
            playerSpeed = (playerSpeed * 0.48f).coerceAtLeast(2.5f)
            monsterDistance = (monsterDistance - 4.2f).coerceAtLeast(0f)

            playTone(ToneGenerator.TONE_SUP_ERROR, 110)
            vibratePhone(80)
            spawnStepParticles(false)

            // Jika typo lebih dari 5 kali (typo ke-6) -> TERSANDUNG & JATUH!
            if (typoCount > maxAllowedTypos) {
                state = GameState.TRIPPED_FALLING
                tripAnimationProgress = 0f
                vibratePhone(350)
            }
        }
    }

    private fun spawnStepParticles(isSuccess: Boolean) {
        val w = width.toFloat()
        val groundY = height * 0.54f
        val runnerX = w * 0.36f
        val color = if (isSuccess) Color.rgb(56, 239, 125) else Color.rgb(255, 65, 75)
        for (i in 0..8) {
            particles.add(
                Particle(
                    x = runnerX + Random.nextFloat() * 40f - 10f,
                    y = groundY - 8f,
                    vx = Random.nextFloat() * -180f - 40f,
                    vy = Random.nextFloat() * -220f - 50f,
                    radius = Random.nextFloat() * 6f + 4f,
                    color = color
                )
            )
        }
    }

    private fun drawNightBackground(canvas: Canvas, w: Float, h: Float, groundY: Float) {
        // Gradasi langit malam angker
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, groundY,
            intArrayOf(
                Color.rgb(9, 11, 26),
                Color.rgb(22, 18, 42),
                Color.rgb(38, 20, 46)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, groundY, bgPaint)
        bgPaint.shader = null

        // Bulan pucat kemerahan
        val moonX = w * 0.82f
        val moonY = h * 0.16f
        val moonRadius = w * 0.075f
        shapePaint.color = Color.argb(45, 255, 90, 90)
        canvas.drawCircle(moonX, moonY, moonRadius * 1.6f, shapePaint)
        shapePaint.color = Color.rgb(245, 215, 195)
        canvas.drawCircle(moonX, moonY, moonRadius, shapePaint)

        // Siluet pepohonan beringin & bambu kejauhan (Parallax Lapis 1)
        shapePaint.color = Color.rgb(16, 13, 30)
        val treeSpacing = 220f
        val offset1 = (worldScrollX * 0.25f) % treeSpacing
        var tx = -offset1
        while (tx < w + treeSpacing) {
            // Batang pohon
            canvas.drawRect(tx + 40f, groundY - 150f, tx + 62f, groundY, shapePaint)
            // Rimbun daun beringin
            canvas.drawCircle(tx + 51f, groundY - 155f, 55f, shapePaint)
            canvas.drawCircle(tx + 20f, groundY - 135f, 40f, shapePaint)
            canvas.drawCircle(tx + 82f, groundY - 135f, 40f, shapePaint)
            tx += treeSpacing
        }

        // Siluet pagar tua / nisan kejauhan (Parallax Lapis 2)
        shapePaint.color = Color.rgb(11, 9, 22)
        val fenceSpacing = 110f
        val offset2 = (worldScrollX * 0.55f) % fenceSpacing
        var fx = -offset2
        while (fx < w + fenceSpacing) {
            canvas.drawRoundRect(
                fx + 15f,
                groundY - 48f,
                fx + 38f,
                groundY,
                8f,
                8f,
                shapePaint
            )
            fx += fenceSpacing
        }

        // Area jurang gelap di bawah jembatan huruf
        bgPaint.shader = LinearGradient(
            0f, groundY, 0f, h,
            Color.rgb(8, 7, 16),
            Color.rgb(3, 3, 8),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, groundY, w, h, bgPaint)
        bgPaint.shader = null
    }

    private fun drawLetterBridge(canvas: Canvas, w: Float, h: Float, groundY: Float) {
        val runnerX = w * 0.36f
        val tileWidth = (w * 0.082f).coerceIn(54f, 88f)
        val tileHeight = (h * 0.056f).coerceIn(42f, 64f)
        val gap = 8f

        // Gambar jalur tanah yang sudah dilewati di belakang pelari
        shapePaint.color = Color.rgb(36, 32, 56)
        canvas.drawRect(0f, groundY, runnerX - tileWidth * 0.4f, groundY + tileHeight * 0.85f, shapePaint)

        // Gambar pijakan huruf untuk kata saat ini (currentWord)
        // Huruf yang sedang dituju berada tepat di depan langkah kaki pelari
        val startTileX = runnerX - (typedIndex * (tileWidth + gap))

        for (i in currentWord.indices) {
            val left = startTileX + i * (tileWidth + gap)
            val right = left + tileWidth
            if (right < -50f || left > w + 50f) continue

            val isTyped = i < typedIndex
            val isCurrentTarget = i == typedIndex

            val top = if (isCurrentTarget) groundY - 4f else groundY
            val bottom = top + tileHeight

            // Warna batu pijakan huruf
            shapePaint.color = when {
                isTyped -> Color.rgb(28, 145, 92) // Sudah diketik -> pijakan kokoh hijau zamrud
                isCurrentTarget -> Color.rgb(235, 168, 32) // Huruf target saat ini -> emas menyala
                else -> Color.rgb(48, 45, 75) // Belum diketik -> bayangan batu
            }
            canvas.drawRoundRect(left, top, right, bottom, 12f, 12f, shapePaint)

            // Garis tepi pijakan
            strokePaint.strokeWidth = if (isCurrentTarget) 4.5f else 2f
            strokePaint.color = when {
                isTyped -> Color.rgb(88, 240, 165)
                isCurrentTarget -> Color.rgb(255, 238, 120)
                else -> Color.rgb(110, 105, 150)
            }
            canvas.drawRoundRect(left, top, right, bottom, 12f, 12f, strokePaint)

            // Teks huruf di dalam pijakan jalan
            textPaint.color = if (isCurrentTarget) Color.rgb(20, 18, 30) else Color.WHITE
            textPaint.textSize = tileHeight * 0.58f
            val textY = top + tileHeight * 0.68f
            canvas.drawText(currentWord[i].toString(), (left + right) / 2f, textY, textPaint)
        }

        // Gambar pratinjau kata berikutnya (nextWord) di ujung jembatan
        val nextWordStartX = startTileX + currentWord.length * (tileWidth + gap) + tileWidth * 0.7f
        for (j in nextWord.indices) {
            val left = nextWordStartX + j * (tileWidth + gap)
            val right = left + tileWidth
            if (left > w) break

            shapePaint.color = Color.argb(130, 35, 33, 55)
            canvas.drawRoundRect(left, groundY + 4f, right, groundY + tileHeight, 10f, 10f, shapePaint)

            textPaint.color = Color.argb(150, 190, 190, 220)
            textPaint.textSize = tileHeight * 0.48f
            canvas.drawText(
                nextWord[j].toString(),
                (left + right) / 2f,
                groundY + tileHeight * 0.68f,
                textPaint
            )
        }
    }

    private fun drawRunnerCharacter(canvas: Canvas, w: Float, groundY: Float) {
        val runnerX = w * 0.36f
        val scale = (w / 1080f).coerceIn(0.65f, 1.3f)

        canvas.save()
        if (state == GameState.TRIPPED_FALLING || (state == GameState.GAME_OVER && typoCount > maxAllowedTypos)) {
            // Animasi tersandung jatuh ke depan (rotasi hingga 85 derajat mencium tanah)
            val angle = (tripAnimationProgress * 85f).coerceAtMost(85f)
            val dropY = tripAnimationProgress * 28f * scale
            canvas.translate(runnerX, groundY + dropY)
            canvas.rotate(angle)
            canvas.translate(-runnerX, -groundY)
        } else {
            // Pantulan lari normal
            val bounce = abs(sin(runnerAnimPhase)) * 12f * scale
            canvas.translate(0f, -bounce)
        }

        val headRadius = 20f * scale
        val bodyTop = groundY - 92f * scale
        val bodyBottom = groundY - 38f * scale

        // Syal merah berkibar ke belakang
        shapePaint.color = Color.rgb(220, 45, 55)
        val scarfWave = sin(runnerAnimPhase * 1.5f) * 8f * scale
        val scarfPath = Path().apply {
            moveTo(runnerX - 4f * scale, bodyTop + 6f * scale)
            lineTo(runnerX - 48f * scale, bodyTop + 14f * scale + scarfWave)
            lineTo(runnerX - 42f * scale, bodyTop + 26f * scale + scarfWave)
            close()
        }
        canvas.drawPath(scarfPath, shapePaint)

        // Ayunan kaki
        val legSwing = sin(runnerAnimPhase) * 26f * scale
        strokePaint.color = Color.rgb(230, 235, 245)
        strokePaint.strokeWidth = 10f * scale
        strokePaint.strokeCap = Paint.Cap.ROUND

        // Kaki kiri & kanan
        canvas.drawLine(runnerX, bodyBottom, runnerX + legSwing, groundY - 4f, strokePaint)
        canvas.drawLine(runnerX, bodyBottom, runnerX - legSwing, groundY - 4f, strokePaint)

        // Badan karakter (jaket biru terang)
        shapePaint.color = Color.rgb(58, 152, 245)
        canvas.drawRoundRect(
            runnerX - 15f * scale,
            bodyTop,
            runnerX + 15f * scale,
            bodyBottom + 6f * scale,
            10f * scale,
            10f * scale,
            shapePaint
        )

        // Ayunan tangan
        strokePaint.color = Color.rgb(245, 205, 175)
        strokePaint.strokeWidth = 8f * scale
        canvas.drawLine(
            runnerX,
            bodyTop + 12f * scale,
            runnerX - legSwing * 0.9f,
            bodyBottom - 4f * scale,
            strokePaint
        )
        canvas.drawLine(
            runnerX,
            bodyTop + 12f * scale,
            runnerX + legSwing * 0.9f,
            bodyBottom - 4f * scale,
            strokePaint
        )

        // Kepala karakter
        shapePaint.color = Color.rgb(248, 212, 182)
        canvas.drawCircle(runnerX + 4f * scale, bodyTop - headRadius * 0.9f, headRadius, shapePaint)

        // Rambut hitam
        shapePaint.color = Color.rgb(25, 25, 35)
        canvas.drawArc(
            runnerX - 16f * scale,
            bodyTop - headRadius * 2.0f,
            runnerX + 24f * scale,
            bodyTop - 4f * scale,
            160f,
            190f,
            true,
            shapePaint
        )

        // Tanda seru panik saat melambat karena typo
        if (slowdownTimer > 0f || state == GameState.TRIPPED_FALLING) {
            textPaint.color = Color.rgb(255, 75, 75)
            textPaint.textSize = 30f * scale
            canvas.drawText(
                if (state == GameState.TRIPPED_FALLING) "TERSANDUNG!" else "MELAMBAT!",
                runnerX,
                bodyTop - headRadius * 2.5f,
                textPaint
            )
        }

        canvas.restore()
    }

    private fun drawUrbanLegendShadow(canvas: Canvas, w: Float, groundY: Float) {
        val runnerX = w * 0.36f
        val proximityRatio = 1f - (monsterDistance / maxMonsterDistance).coerceIn(0f, 1f)
        // Posisi X bayangan monster bergerak mendekati runnerX saat monsterDistance mengecil
        val monsterX = (-w * 0.06f) + proximityRatio * (runnerX - w * 0.02f)
        val scale = (w / 1080f).coerceIn(0.65f, 1.3f)
        val floatOffset = sin(monsterAnimPhase) * 14f * scale

        // Kabut aura kegelapan di sisi kiri layar
        bgPaint.shader = LinearGradient(
            0f, 0f, (monsterX + 140f * scale).coerceAtLeast(60f), 0f,
            Color.argb(220, 5, 0, 12),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, (monsterX + 140f * scale).coerceAtLeast(60f), groundY + 40f, bgPaint)
        bgPaint.shader = null

        // Tubuh bayangan raksasa Urban Legend
        val monsterTop = groundY - 230f * scale + floatOffset
        shapePaint.color = Color.argb(235, 12, 8, 20)

        // Siluet tubuh utama + sulur bayangan
        val shadowPath = Path().apply {
            moveTo(monsterX - 120f * scale, groundY + 20f)
            quadTo(monsterX - 90f * scale, monsterTop + 40f * scale, monsterX - 15f * scale, monsterTop)
            quadTo(monsterX + 55f * scale, monsterTop + 20f * scale, monsterX + 45f * scale, groundY + 20f)
            close()
        }
        canvas.drawPath(shadowPath, shapePaint)

        // Tanduk / rambut menjuntai tergantung jenis monster
        when (currentMonsterIndex) {
            0 -> { // Genderuwo: bertubuh besar & bertanduk bayangan
                canvas.drawCircle(monsterX - 15f * scale, monsterTop + 20f * scale, 52f * scale, shapePaint)
            }
            1 -> { // Kuntilanak: rambut panjang melambai
                val hairWave = cos(monsterAnimPhase * 2f) * 18f * scale
                canvas.drawRect(
                    monsterX - 55f * scale,
                    monsterTop,
                    monsterX + 25f * scale,
                    groundY - 30f * scale + hairWave,
                    shapePaint
                )
            }
            2 -> { // Banaspati: bola api berbayang merah
                shapePaint.color = Color.argb(110, 220, 40, 20)
                canvas.drawCircle(monsterX - 10f * scale, monsterTop + 65f * scale, 68f * scale, shapePaint)
                shapePaint.color = Color.argb(235, 12, 8, 20)
            }
            else -> { // Wewe Gombel: siluet membungkuk panjang
                canvas.drawCircle(monsterX + 5f * scale, monsterTop + 30f * scale, 46f * scale, shapePaint)
            }
        }

        // Tangan cakar bayangan yang menjulur ke arah karakter
        val clawReach = sin(monsterAnimPhase * 1.8f) * 18f * scale
        strokePaint.color = Color.argb(240, 18, 10, 28)
        strokePaint.strokeWidth = 14f * scale
        canvas.drawLine(
            monsterX + 10f * scale,
            monsterTop + 95f * scale,
            monsterX + 78f * scale + clawReach,
            monsterTop + 110f * scale,
            strokePaint
        )

        // Mata merah menyala di dalam kegelapan
        val eyeY = monsterTop + 42f * scale
        shapePaint.color = Color.rgb(255, 30, 45)
        canvas.drawCircle(monsterX + 8f * scale, eyeY, 8.5f * scale, shapePaint)
        canvas.drawCircle(monsterX + 28f * scale, eyeY + 2f * scale, 7f * scale, shapePaint)

        // Kilau cahaya mata
        shapePaint.color = Color.argb(90, 255, 60, 60)
        canvas.drawCircle(monsterX + 16f * scale, eyeY, 24f * scale, shapePaint)
    }

    private fun drawParticles(canvas: Canvas) {
        for (p in particles) {
            shapePaint.color = Color.argb(
                p.alpha.coerceIn(0, 255),
                Color.red(p.color),
                Color.green(p.color),
                Color.blue(p.color)
            )
            canvas.drawCircle(p.x, p.y, p.radius, shapePaint)
        }
    }

    private fun drawHud(canvas: Canvas, w: Float, h: Float) {
        val pad = w * 0.035f

        // Panel atas HUD
        shapePaint.color = Color.argb(185, 14, 16, 32)
        canvas.drawRoundRect(pad, h * 0.02f, w - pad, h * 0.17f, 22f, 22f, shapePaint)

        // Skor & Jarak Lari
        hudPaint.color = Color.WHITE
        hudPaint.textSize = (w * 0.036f).coerceIn(26f, 42f)
        hudPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("SKOR: $score", pad * 1.8f, h * 0.062f, hudPaint)

        hudPaint.color = Color.rgb(160, 210, 255)
        hudPaint.textSize = (w * 0.030f).coerceIn(22f, 34f)
        canvas.drawText(
            "Pengejar: ${monsterNames[currentMonsterIndex]} (${monsterDistance.toInt()}m)",
            pad * 1.8f,
            h * 0.102f,
            hudPaint
        )

        // Indikator Salah Ketik (Typo) - Maksimal 5x, lebih dari 5x jatuh!
        hudPaint.textAlign = Paint.Align.RIGHT
        val typoColor = when {
            typoCount >= 4 -> Color.rgb(255, 70, 70)
            typoCount >= 2 -> Color.rgb(255, 190, 60)
            else -> Color.rgb(120, 240, 160)
        }
        hudPaint.color = typoColor
        canvas.drawText(
            "SALAH KETIK: $typoCount / $maxAllowedTypos",
            w - pad * 1.8f,
            h * 0.062f,
            hudPaint
        )

        // Bar Jarak Monster
        val barLeft = pad * 1.8f
        val barRight = w - pad * 1.8f
        val barTop = h * 0.125f
        val barBottom = h * 0.148f
        shapePaint.color = Color.rgb(40, 35, 60)
        canvas.drawRoundRect(barLeft, barTop, barRight, barBottom, 10f, 10f, shapePaint)

        val safeRatio = (monsterDistance / maxMonsterDistance).coerceIn(0f, 1f)
        shapePaint.color = when {
            safeRatio < 0.28f -> Color.rgb(240, 50, 60)
            safeRatio < 0.55f -> Color.rgb(245, 175, 40)
            else -> Color.rgb(55, 215, 130)
        }
        canvas.drawRoundRect(
            barLeft,
            barTop,
            barLeft + (barRight - barLeft) * safeRatio,
            barBottom,
            10f,
            10f,
            shapePaint
        )

        // Kotak panduan Kata Aktif di tengah atas agar mudah dibaca saat mengetik cepat
        val wordBoxTop = h * 0.19f
        val wordBoxBottom = h * 0.275f
        shapePaint.color = Color.argb(200, 20, 22, 44)
        canvas.drawRoundRect(w * 0.12f, wordBoxTop, w * 0.88f, wordBoxBottom, 20f, 20f, shapePaint)

        strokePaint.color = Color.rgb(235, 175, 45)
        strokePaint.strokeWidth = 3f
        canvas.drawRoundRect(w * 0.12f, wordBoxTop, w * 0.88f, wordBoxBottom, 20f, 20f, strokePaint)

        // Tampilkan kata utuh dengan warna berbeda untuk huruf yang sudah diketik
        val charSpacing = (w * 0.66f / currentWord.length.coerceAtLeast(6)).coerceAtMost(w * 0.068f)
        val startX = (w / 2f) - ((currentWord.length - 1) * charSpacing / 2f)
        val charY = (wordBoxTop + wordBoxBottom) / 2f + (w * 0.018f)

        textPaint.textSize = (w * 0.052f).coerceIn(34f, 60f)
        for (i in currentWord.indices) {
            textPaint.color = when {
                i < typedIndex -> Color.rgb(75, 245, 150) // Sudah diketik
                i == typedIndex -> Color.rgb(255, 215, 55) // Huruf yang harus diketik sekarang
                else -> Color.WHITE
            }
            val cx = startX + i * charSpacing
            canvas.drawText(currentWord[i].toString(), cx, charY, textPaint)
            if (i == typedIndex) {
                // Garis bawah penanda huruf aktif
                shapePaint.color = Color.rgb(255, 215, 55)
                canvas.drawRect(
                    cx - charSpacing * 0.35f,
                    charY + 8f,
                    cx + charSpacing * 0.35f,
                    charY + 14f,
                    shapePaint
                )
            }
        }
    }

    private fun drawVirtualKeyboard(canvas: Canvas, w: Float, h: Float) {
        val kbTop = h * 0.64f
        // Panel dasar keyboard virtual
        shapePaint.color = Color.rgb(15, 16, 28)
        canvas.drawRect(0f, kbTop, w, h, shapePaint)

        strokePaint.color = Color.rgb(55, 52, 88)
        strokePaint.strokeWidth = 3f
        canvas.drawLine(0f, kbTop, w, kbTop, strokePaint)

        val targetChar = if (state == GameState.PLAYING && typedIndex < currentWord.length) {
            currentWord[typedIndex]
        } else {
            null
        }

        for (btn in keyButtons) {
            val isPressed = btn.pressAnim > 0f
            val isTargetHint = btn.char == targetChar

            shapePaint.color = when {
                isPressed -> Color.rgb(245, 185, 45)
                isTargetHint -> Color.rgb(44, 58, 98)
                else -> Color.rgb(32, 34, 56)
            }

            val inset = if (isPressed) 4f else 0f
            canvas.drawRoundRect(
                btn.rect.left + inset,
                btn.rect.top + inset,
                btn.rect.right - inset,
                btn.rect.bottom - inset,
                14f,
                14f,
                shapePaint
            )

            strokePaint.strokeWidth = if (isTargetHint) 3.5f else 2f
            strokePaint.color = if (isTargetHint) {
                Color.rgb(255, 210, 75)
            } else {
                Color.rgb(75, 78, 115)
            }
            canvas.drawRoundRect(
                btn.rect.left + inset,
                btn.rect.top + inset,
                btn.rect.right - inset,
                btn.rect.bottom - inset,
                14f,
                14f,
                strokePaint
            )

            textPaint.color = if (isPressed) Color.rgb(20, 20, 30) else Color.WHITE
            textPaint.textSize = (btn.rect.height() * 0.44f).coerceIn(26f, 48f)
            val textY = btn.rect.centerY() + textPaint.textSize * 0.35f
            canvas.drawText(btn.char.toString(), btn.rect.centerX(), textY, textPaint)
        }
    }

    private fun drawOverlayScreen(canvas: Canvas, w: Float, h: Float) {
        shapePaint.color = Color.argb(215, 8, 9, 18)
        canvas.drawRect(0f, 0f, w, h, shapePaint)

        val cardLeft = w * 0.08f
        val cardRight = w * 0.92f
        val cardTop = h * 0.18f
        val cardBottom = h * 0.84f

        shapePaint.color = Color.rgb(22, 24, 42)
        canvas.drawRoundRect(cardLeft, cardTop, cardRight, cardBottom, 32f, 32f, shapePaint)

        strokePaint.color = Color.rgb(225, 65, 75)
        strokePaint.strokeWidth = 4f
        canvas.drawRoundRect(cardLeft, cardTop, cardRight, cardBottom, 32f, 32f, strokePaint)

        textPaint.textAlign = Paint.Align.CENTER

        if (state == GameState.MENU) {
            textPaint.color = Color.rgb(255, 210, 65)
            textPaint.textSize = (w * 0.068f).coerceIn(42f, 76f)
            canvas.drawText("LARI KETIK", w / 2f, cardTop + h * 0.10f, textPaint)

            textPaint.color = Color.rgb(235, 85, 95)
            textPaint.textSize = (w * 0.042f).coerceIn(28f, 48f)
            canvas.drawText("BAYANGAN URBAN LEGEND", w / 2f, cardTop + h * 0.16f, textPaint)

            textPaint.color = Color.rgb(210, 215, 235)
            textPaint.textSize = (w * 0.033f).coerceIn(22f, 36f)
            val rules = listOf(
                "• Ketik kata Bahasa Indonesia untuk membentuk jalan.",
                "• Setiap huruf yang benar mempercepat langkah larimu.",
                "• Salah ketik (typo) membuat karakter MELAMBAT!",
                "• Lebih dari 5x salah ketik = TERSANDUNG & JATUH!"
            )
            for (i in rules.indices) {
                canvas.drawText(rules[i], w / 2f, cardTop + h * (0.25f + i * 0.055f), textPaint)
            }
        } else {
            textPaint.color = Color.rgb(255, 75, 85)
            textPaint.textSize = (w * 0.065f).coerceIn(40f, 72f)
            canvas.drawText("PERMAINAN BERAKHIR", w / 2f, cardTop + h * 0.10f, textPaint)

            textPaint.color = Color.rgb(255, 215, 75)
            textPaint.textSize = (w * 0.048f).coerceIn(30f, 52f)
            canvas.drawText("Skor Akhir: $score", w / 2f, cardTop + h * 0.18f, textPaint)

            textPaint.color = Color.rgb(180, 220, 255)
            textPaint.textSize = (w * 0.035f).coerceIn(24f, 38f)
            canvas.drawText(
                "Kata Selesai: $wordsCompleted  |  Salah Ketik: $typoCount / $maxAllowedTypos",
                w / 2f,
                cardTop + h * 0.24f,
                textPaint
            )

            // Alasan kalah
            textPaint.color = Color.WHITE
            textPaint.textSize = (w * 0.032f).coerceIn(22f, 35f)
            drawWrappedText(canvas, gameOverReason, w / 2f, cardTop + h * 0.33f, w * 0.72f)
        }

        // Tombol Mulai / Main Lagi
        shapePaint.color = Color.rgb(225, 55, 68)
        canvas.drawRoundRect(startButtonRect, 24f, 24f, shapePaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = (w * 0.045f).coerceIn(28f, 48f)
        val btnLabel = if (state == GameState.MENU) "MULAI BERLARI" else "COBA LAGI"
        canvas.drawText(
            btnLabel,
            startButtonRect.centerX(),
            startButtonRect.centerY() + textPaint.textSize * 0.35f,
            textPaint
        )
    }

    private fun drawWrappedText(canvas: Canvas, text: String, cx: Float, startY: Float, maxWidth: Float) {
        val words = text.split(" ")
        var line = ""
        var y = startY
        for (word in words) {
            val testLine = if (line.isEmpty()) word else "$line $word"
            if (textPaint.measureText(testLine) > maxWidth) {
                canvas.drawText(line, cx, y, textPaint)
                line = word
                y += textPaint.textSize * 1.35f
            } else {
                line = testLine
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line, cx, y, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_POINTER_DOWN) {
            val actionIndex = event.actionIndex
            val tx = event.getX(actionIndex)
            val ty = event.getY(actionIndex)

            if (state == GameState.MENU || state == GameState.GAME_OVER) {
                if (startButtonRect.contains(tx, ty)) {
                    startNewGame()
                }
                return true
            }

            if (state == GameState.PLAYING) {
                for (btn in keyButtons) {
                    if (btn.rect.contains(tx, ty)) {
                        btn.pressAnim = 1f
                        handleKeyInput(btn.char)
                        break
                    }
                }
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun playTone(toneType: Int, durationMs: Int) {
        try {
            toneGenerator?.startTone(toneType, durationMs)
        } catch (_: Exception) {
        }
    }

    private fun vibratePhone(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {
        }
    }
}
