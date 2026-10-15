package app.votify.mobile.ui.account

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.votify.mobile.R
import app.votify.mobile.data.DeezerApi
import app.votify.mobile.data.DeezerArtist
import app.votify.mobile.ui.theme.VotifyColors
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private val DeezerPurple = Color(0xFFA238FF)
private val DarkCardBg = Color(0xFF18181E)
private val DarkCardBorder = Color(0xFF2A2A34)

enum class OnboardingStep {
    WELCOME,
    EMAIL,
    PASSWORD,
    USERNAME,
    AGE,
    IDENTITY,
    PRIOR_APP,
    IMPORT_LIBRARY,
    ARTIST_PICKER,
    WORLD_CREATION,
}

/**
 * Deezer-style multi-step registration and onboarding flow:
 * 1. Welcome screen with purple wave header
 * 2. Email step
 * 3. Password step with live rule validation
 * 4. Username step
 * 5. Age step (16+)
 * 6. Identity / Gender step
 * 7. Prior app survey
 * 8. Media library import prompt
 * 9. Artist picker loaded from Deezer API
 * 10. Final "Your music world is coming to life" animation
 */
@Composable
fun WelcomeOverlay(
    onSignIn: () -> Unit,
    onGuest: () -> Unit,
    onRegister: ((email: String, username: String, pass: String) -> Unit)? = null,
    onImport: (() -> Unit)? = null,
) {
    var step by remember { mutableStateOf(OnboardingStep.WELCOME) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Не указывать") }
    var hasUsedOtherApp by remember { mutableStateOf<Boolean?>(null) }
    val selectedArtists = remember { mutableStateListOf<DeezerArtist>() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
            label = "onboarding_steps",
            modifier = Modifier.fillMaxSize(),
        ) { currentStep ->
            when (currentStep) {
                OnboardingStep.WELCOME -> WelcomeStep(
                    onContinueEmail = { step = OnboardingStep.EMAIL },
                    onGoogle = onSignIn,
                    onGuest = onGuest,
                )
                OnboardingStep.EMAIL -> EmailStep(
                    email = email,
                    onEmailChange = { email = it },
                    onNext = { step = OnboardingStep.PASSWORD },
                    onBack = { step = OnboardingStep.WELCOME },
                    onGuest = onGuest,
                )
                OnboardingStep.PASSWORD -> PasswordStep(
                    password = password,
                    onPasswordChange = { password = it },
                    onNext = { step = OnboardingStep.USERNAME },
                    onBack = { step = OnboardingStep.EMAIL },
                    onSignIn = onSignIn,
                )
                OnboardingStep.USERNAME -> UsernameStep(
                    username = username,
                    onUsernameChange = { username = it },
                    onNext = { step = OnboardingStep.AGE },
                    onBack = { step = OnboardingStep.PASSWORD },
                )
                OnboardingStep.AGE -> AgeStep(
                    age = age,
                    onAgeChange = { age = it.filter { ch -> ch.isDigit() }.take(3) },
                    onNext = { step = OnboardingStep.IDENTITY },
                    onBack = { step = OnboardingStep.USERNAME },
                )
                OnboardingStep.IDENTITY -> IdentityStep(
                    selectedGender = gender,
                    onSelectGender = { gender = it },
                    onSubmit = {
                        onRegister?.invoke(email, username, password)
                        step = OnboardingStep.PRIOR_APP
                    },
                    onBack = { step = OnboardingStep.AGE },
                )
                OnboardingStep.PRIOR_APP -> PriorAppStep(
                    hasUsedOtherApp = hasUsedOtherApp,
                    onSelect = { hasUsedOtherApp = it },
                    onNext = { step = OnboardingStep.IMPORT_LIBRARY },
                    onSkip = { step = OnboardingStep.ARTIST_PICKER },
                )
                OnboardingStep.IMPORT_LIBRARY -> ImportLibraryStep(
                    onImport = {
                        onImport?.invoke()
                        step = OnboardingStep.ARTIST_PICKER
                    },
                    onSkip = { step = OnboardingStep.ARTIST_PICKER },
                )
                OnboardingStep.ARTIST_PICKER -> ArtistPickerStep(
                    selectedArtists = selectedArtists,
                    onToggleArtist = { artist ->
                        if (selectedArtists.any { it.id == artist.id }) {
                            selectedArtists.removeAll { it.id == artist.id }
                        } else {
                            selectedArtists.add(artist)
                        }
                    },
                    onNext = { step = OnboardingStep.WORLD_CREATION },
                    onSkip = onGuest,
                )
                OnboardingStep.WORLD_CREATION -> WorldCreationStep(
                    selectedArtists = selectedArtists,
                    onComplete = onGuest,
                )
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 0: WELCOME
// -------------------------------------------------------------
@Composable
private fun WelcomeStep(
    onContinueEmail: () -> Unit,
    onGoogle: () -> Unit,
    onGuest: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top purple wave header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(w, h * 0.45f)
                    cubicTo(
                        w * 0.75f, h * 0.85f,
                        w * 0.25f, h * 0.25f,
                        0f, h * 0.7f,
                    )
                    close()
                }
                drawPath(path, color = DeezerPurple.copy(alpha = 0.85f))
            }
            Surface(
                shape = CircleShape,
                color = DarkCardBg,
                border = BorderStroke(1.dp, DarkCardBorder),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 28.dp),
            ) {
                Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.ic_votify_logo),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        Text(
            text = "WELCOME TO VOTIFY",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "Миллионы треков, альбомы и «Моя волна»\nбез рекламы и ограничений",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFA1A1AA),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Main Deezer-style Purple CTA
            Surface(
                onClick = onContinueEmail,
                shape = RoundedCornerShape(28.dp),
                color = DeezerPurple,
                contentColor = Color.White,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Продолжить с Email",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Or separator
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).height(0.5.dp).background(DarkCardBorder))
                Text(
                    text = "или",
                    color = Color(0xFF71717A),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Box(Modifier.weight(1f).height(0.5.dp).background(DarkCardBorder))
            }

            Spacer(Modifier.height(14.dp))

            // Google sign in option
            Surface(
                onClick = onGoogle,
                shape = RoundedCornerShape(28.dp),
                color = DarkCardBg,
                border = BorderStroke(0.5.dp, DarkCardBorder),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Войти через Google",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Guest option
            Text(
                text = "Продолжить как гость",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF71717A),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onGuest)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )

            Spacer(Modifier.height(18.dp))
        }
    }
}

// -------------------------------------------------------------
// STEP 1: EMAIL
// -------------------------------------------------------------
@Composable
private fun EmailStep(
    email: String,
    onEmailChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onGuest: () -> Unit,
) {
    val isValid = email.contains('@') && email.contains('.')

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        TopBarNav(onBack = onBack, onSkip = onGuest)

        Spacer(Modifier.height(28.dp))

        Text(
            text = "Какой у вас email?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Мы отправим подтверждение или войдём в ваш существующий аккаунт Votify.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFA1A1AA),
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            placeholder = { Text("name@example.com", color = Color(0xFF71717A)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkCardBg,
                unfocusedContainerColor = DarkCardBg,
                focusedBorderColor = DeezerPurple,
                unfocusedBorderColor = DarkCardBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.weight(1f))

        PrimaryButton(
            text = "Продолжить",
            enabled = isValid,
            onClick = onNext,
        )

        Spacer(Modifier.height(20.dp))
    }
}

// -------------------------------------------------------------
// STEP 2: PASSWORD
// -------------------------------------------------------------
@Composable
private fun PasswordStep(
    password: String,
    onPasswordChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }

    val hasLength = password.length >= 8
    val hasLetter = password.any { it.isLetter() }
    val hasDigit = password.any { it.isDigit() || !it.isLetterOrDigit() }
    val allValid = hasLength && hasLetter && hasDigit

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        TopBarNav(onBack = onBack)

        Spacer(Modifier.height(28.dp))

        Text(
            text = "Создайте пароль",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Используйте надёжный пароль для защиты вашего профиля и медиатеки.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFA1A1AA),
        )

        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            placeholder = { Text("Пароль", color = Color(0xFF71717A)) },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null,
                        tint = Color(0xFFA1A1AA),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkCardBg,
                unfocusedContainerColor = DarkCardBg,
                focusedBorderColor = DeezerPurple,
                unfocusedBorderColor = DarkCardBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))

        // Live validation rules checklist
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkCardBg,
            border = BorderStroke(0.5.dp, DarkCardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PasswordRuleRow(text = "Не менее 8 символов", passed = hasLength)
                PasswordRuleRow(text = "Минимум 1 буква", passed = hasLetter)
                PasswordRuleRow(text = "Минимум 1 цифра или символ", passed = hasDigit)
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Уже есть аккаунт? Войти",
                style = MaterialTheme.typography.bodyMedium,
                color = DeezerPurple,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onSignIn)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        PrimaryButton(
            text = "Продолжить",
            enabled = allValid,
            onClick = onNext,
        )

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun PasswordRuleRow(text: String, passed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (passed) Color(0xFF22C55E) else Color(0xFF27272A)),
            contentAlignment = Alignment.Center,
        ) {
            if (passed) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (passed) Color.White else Color(0xFF71717A),
        )
    }
}

// -------------------------------------------------------------
// STEP 3: USERNAME
// -------------------------------------------------------------
@Composable
private fun UsernameStep(
    username: String,
    onUsernameChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        TopBarNav(onBack = onBack)

        Spacer(Modifier.height(28.dp))

        Text(
            text = "Как к вам обращаться?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Выберите никнейм — его будут видеть ваши друзья в Votify.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFA1A1AA),
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            placeholder = { Text("Никнейм (например, exieeez)", color = Color(0xFF71717A)) },
            singleLine = true,
            trailingIcon = {
                if (username.isNotBlank()) {
                    IconButton(onClick = { onUsernameChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color(0xFFA1A1AA))
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkCardBg,
                unfocusedContainerColor = DarkCardBg,
                focusedBorderColor = DeezerPurple,
                unfocusedBorderColor = DarkCardBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.weight(1f))

        PrimaryButton(
            text = "Продолжить",
            enabled = username.isNotBlank(),
            onClick = onNext,
        )

        Spacer(Modifier.height(20.dp))
    }
}

// -------------------------------------------------------------
// STEP 4: AGE
// -------------------------------------------------------------
@Composable
private fun AgeStep(
    age: String,
    onAgeChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val ageNum = age.toIntOrNull()
    val isValid = ageNum != null && ageNum in 6..120

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        TopBarNav(onBack = onBack)

        Spacer(Modifier.height(28.dp))

        Text(
            text = "Сколько вам лет?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Это поможет подобрать более точные музыкальные подборки и чарты.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFA1A1AA),
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = age,
            onValueChange = onAgeChange,
            placeholder = { Text("Возраст (например, 21)", color = Color(0xFF71717A)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkCardBg,
                unfocusedContainerColor = DarkCardBg,
                focusedBorderColor = DeezerPurple,
                unfocusedBorderColor = DarkCardBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.weight(1f))

        PrimaryButton(
            text = "Продолжить",
            enabled = isValid,
            onClick = onNext,
        )

        Spacer(Modifier.height(20.dp))
    }
}

// -------------------------------------------------------------
// STEP 5: IDENTITY (Gender)
// -------------------------------------------------------------
@Composable
private fun IdentityStep(
    selectedGender: String,
    onSelectGender: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val genders = listOf("Мужской", "Женский", "Не указывать")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        TopBarNav(onBack = onBack)

        Spacer(Modifier.height(28.dp))

        Text(
            text = "Укажите ваш пол",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Информация используется только для персонализации рекомендаций.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFA1A1AA),
        )

        Spacer(Modifier.height(28.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            genders.forEach { g ->
                val isSelected = selectedGender == g
                Surface(
                    onClick = { onSelectGender(g) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) Color(0xFF231A33) else DarkCardBg,
                    border = BorderStroke(if (isSelected) 1.5.dp else 0.5.dp, if (isSelected) DeezerPurple else DarkCardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = g,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = Color.White,
                        )
                        Icon(
                            if (isSelected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected) DeezerPurple else Color(0xFF71717A),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        PrimaryButton(
            text = "Создать аккаунт",
            enabled = true,
            onClick = onSubmit,
        )

        Spacer(Modifier.height(20.dp))
    }
}

// -------------------------------------------------------------
// STEP 6: PRIOR APP SURVEY
// -------------------------------------------------------------
@Composable
private fun PriorAppStep(
    hasUsedOtherApp: Boolean?,
    onSelect: (Boolean) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        TopBarNav(onSkip = onSkip)

        Spacer(Modifier.height(28.dp))

        Text(
            text = "Пользовались ли вы другими сервисами?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Это поможет быстро настроить стартовые алгоритмы подбора треков.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFA1A1AA),
        )

        Spacer(Modifier.height(32.dp))

        Surface(
            onClick = { onSelect(true) },
            shape = RoundedCornerShape(16.dp),
            color = if (hasUsedOtherApp == true) Color(0xFF231A33) else DarkCardBg,
            border = BorderStroke(if (hasUsedOtherApp == true) 1.5.dp else 0.5.dp, if (hasUsedOtherApp == true) DeezerPurple else DarkCardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Да, слушал(а) музыку в других приложениях",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (hasUsedOtherApp == true) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (hasUsedOtherApp == true) DeezerPurple else Color(0xFF71717A),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            onClick = { onSelect(false) },
            shape = RoundedCornerShape(16.dp),
            color = if (hasUsedOtherApp == false) Color(0xFF231A33) else DarkCardBg,
            border = BorderStroke(if (hasUsedOtherApp == false) 1.5.dp else 0.5.dp, if (hasUsedOtherApp == false) DeezerPurple else DarkCardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Нет, это мой первый музыкальный плеер",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (hasUsedOtherApp == false) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (hasUsedOtherApp == false) DeezerPurple else Color(0xFF71717A),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        PrimaryButton(
            text = "Продолжить",
            enabled = hasUsedOtherApp != null,
            onClick = onNext,
        )

        Spacer(Modifier.height(20.dp))
    }
}

// -------------------------------------------------------------
// STEP 7: IMPORT LIBRARY
// -------------------------------------------------------------
@Composable
private fun ImportLibraryStep(
    onImport: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        TopBarNav(onSkip = onSkip)

        Spacer(Modifier.height(28.dp))

        Text(
            text = "Перенесите свою медиатеку в Votify",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Импортируйте любимые плейлисты из Spotify, Яндекс Музыки, YouTube или SoundCloud за пару секунд.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFA1A1AA),
        )

        Spacer(Modifier.height(36.dp))

        // Visual services illustration card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkCardBg,
            border = BorderStroke(0.5.dp, DarkCardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ServiceBadge(name = "Spotify", color = Color(0xFF1DB954))
                    ServiceBadge(name = "Яндекс", color = Color(0xFFFFCC00))
                    ServiceBadge(name = "YouTube", color = Color(0xFFFF0000))
                    ServiceBadge(name = "SoundCloud", color = Color(0xFFFF5500))
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Все треки найдутся автоматически и будут доступны в высоком качестве.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF71717A),
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        PrimaryButton(
            text = "Импортировать медиатеку",
            enabled = true,
            onClick = onImport,
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Сделать это позже",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF71717A),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ServiceBadge(name: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0xFF27272A))
                .border(1.dp, color.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.take(1),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFA1A1AA),
        )
    }
}

// -------------------------------------------------------------
// STEP 8: ARTIST PICKER (Deezer API Integration)
// -------------------------------------------------------------
@Composable
private fun ArtistPickerStep(
    selectedArtists: List<DeezerArtist>,
    onToggleArtist: (DeezerArtist) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val genres = listOf("Популярное", "Рэп", "Рок", "Phonk", "R&B", "Электроника")
    var selectedGenre by remember { mutableStateOf("Популярное") }
    var artists by remember { mutableStateOf<List<DeezerArtist>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(selectedGenre) {
        loading = true
        val genreKey = when (selectedGenre) {
            "Рэп" -> "rap"
            "Рок" -> "rock"
            "Phonk" -> "phonk"
            "R&B" -> "r&b"
            "Электроника" -> "electronic"
            else -> "pop"
        }
        artists = DeezerApi.getCuratedArtists(genreKey, limit = 16)
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        TopBarNav(onSkip = onSkip)

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Выберите артистов",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "От 3 до 10 исполнителей",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA1A1AA),
                )
            }
            Surface(
                shape = CircleShape,
                color = if (selectedArtists.size >= 3) DeezerPurple else DarkCardBg,
                border = BorderStroke(0.5.dp, if (selectedArtists.size >= 3) DeezerPurple else DarkCardBorder),
            ) {
                Text(
                    text = "${selectedArtists.size} / 10",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Genre filter chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(genres) { g ->
                val active = selectedGenre == g
                Surface(
                    onClick = { selectedGenre = g },
                    shape = RoundedCornerShape(20.dp),
                    color = if (active) DeezerPurple else DarkCardBg,
                    border = BorderStroke(0.5.dp, if (active) DeezerPurple else DarkCardBorder),
                ) {
                    Text(
                        text = g,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 2-column grid of curated artists with Deezer photos
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(color = DeezerPurple, strokeWidth = 2.dp)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(artists) { artist ->
                        val isSelected = selectedArtists.any { it.id == artist.id }
                        Surface(
                            onClick = { onToggleArtist(artist) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) Color(0xFF231A33) else DarkCardBg,
                            border = BorderStroke(if (isSelected) 1.5.dp else 0.5.dp, if (isSelected) DeezerPurple else DarkCardBorder),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    if (artist.picture.isNotBlank()) {
                                        AsyncImage(
                                            model = artist.picture,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(CircleShape)
                                                .border(
                                                    if (isSelected) 2.dp else 0.5.dp,
                                                    if (isSelected) DeezerPurple else Color(0xFF27272A),
                                                    CircleShape,
                                                ),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF27272A)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = artist.name.take(1),
                                                style = MaterialTheme.typography.titleLarge,
                                                color = Color.White,
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(DeezerPurple),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = artist.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                                if (artist.nbFan > 0) {
                                    Text(
                                        text = "${artist.nbFan / 1000}k фанатов",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF71717A),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        PrimaryButton(
            text = if (selectedArtists.size >= 3) "Продолжить" else "Выбрать (${selectedArtists.size})",
            enabled = selectedArtists.isNotEmpty(),
            onClick = onNext,
        )

        Spacer(Modifier.height(18.dp))
    }
}

// -------------------------------------------------------------
// STEP 9: WORLD CREATION ANIMATION
// -------------------------------------------------------------
@Composable
private fun WorldCreationStep(
    selectedArtists: List<DeezerArtist>,
    onComplete: () -> Unit,
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2400, easing = LinearEasing),
        )
        onComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Overlapping circles of selected artists
        Box(
            modifier = Modifier
                .size(160.dp)
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            val display = selectedArtists.take(4)
            val offsets = listOf(
                Pair(-24.dp, -24.dp),
                Pair(24.dp, -24.dp),
                Pair(-24.dp, 24.dp),
                Pair(24.dp, 24.dp),
            )
            display.forEachIndexed { i, a ->
                val (ox, oy) = offsets.getOrElse(i) { Pair(0.dp, 0f.dp) }
                Box(
                    modifier = Modifier
                        .offset(x = ox, y = oy)
                        .size(68.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color.Black, CircleShape)
                        .background(Color(0xFF27272A)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (a.picture.isNotBlank()) {
                        AsyncImage(
                            model = a.picture,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(a.name.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(36.dp))

        Text(
            text = "Создаём ваш музыкальный мир...",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Анализируем ваших любимых артистов и настраиваем стартовую «Мою волну».",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFA1A1AA),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        // Purple progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(6.dp)
                .clip(CircleShape)
                .background(Color(0xFF27272A)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.value)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(DeezerPurple),
            )
        }
    }
}

// -------------------------------------------------------------
// REUSABLE COMPONENTS
// -------------------------------------------------------------
@Composable
private fun TopBarNav(
    onBack: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }

        if (onSkip != null) {
            Text(
                text = "Пропустить",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF71717A),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        color = if (enabled) DeezerPurple else Color(0xFF27272A),
        contentColor = if (enabled) Color.White else Color(0xFF71717A),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
