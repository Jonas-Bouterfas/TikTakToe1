package com.example.tiktaktoe


import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow



@Composable
fun TicTacToe2() {
    val navController = rememberNavController()
    val model = GameModel()
    model.initGame()

    NavHost(navController = navController, startDestination = "player") {
        composable("player") { NewPlayerScreen(navController, model) }
        composable("lobby") { LobbyScreen(navController, model) }
        composable("game/{gameId}") { backStackEntry ->
            val gameId = backStackEntry.arguments?.getString("gameId")
            GameScreen(navController, model, gameId)
        }
    }
}

@Composable
fun NewPlayerScreen(navController: NavController, model: GameModel) {
    val sharedPreferences = LocalContext.current
        .getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)

    // Check for playerId in SharedPreferences
    LaunchedEffect(Unit) {
        model.localPlayerId.value = sharedPreferences.getString("playerId", null)
        if (model.localPlayerId.value != null) {
            navController.navigate("lobby")
        }
    }

    if (model.localPlayerId.value == null) {

        var playerName by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Welcome to Jonas TicTacToe!")

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = playerName,
                onValueChange = { playerName = it },
                label = { Text("Enter your name") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { if (playerName.isNotBlank()) {
                    // Create new player in Firestore
                    val newPlayer = Player(name = playerName)

                    model.db.collection("players")
                        .add(newPlayer)
                        .addOnSuccessListener { documentRef ->
                            val newPlayerId = documentRef.id

                            // Save playerId in SharedPreferences
                            sharedPreferences.edit().putString("playerId", newPlayerId).apply()

                            // Update local variable and navigate to lobby
                            model.localPlayerId.value = newPlayerId
                            navController.navigate("lobby")
                        }.addOnFailureListener { error ->
                            Log.e("Error", "Error creating player: ${error.message}")
                        }
                } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create Player")
            }
        }
    } else {
        Text("Laddar....")
    }
}

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun LobbyScreen(navController: NavController, model: GameModel) {
    val players by model.playerMap.asStateFlow().collectAsStateWithLifecycle()
    val games by model.gameMap.asStateFlow().collectAsStateWithLifecycle()

    // Snackbar state
    val snackbarHostState = remember { SnackbarHostState() }
    var incomingGameId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(games) {
        games.forEach { (gameId, game) ->
            if (game.player2Id == model.localPlayerId.value && game.gameState == "invite") {
                // Notify about the challenge
                incomingGameId = gameId
                snackbarHostState.showSnackbar("You have been challenged by ${players[game.player1Id]?.name ?: "Unknown"}!")
            } else if ((game.player1Id == model.localPlayerId.value || game.player2Id == model.localPlayerId.value)
                && (game.gameState == "player1_turn" || game.gameState == "player2_turn")) {
                navController.navigate("game/${gameId}")
            }
        }
    }


    Scaffold(
        topBar = { TopAppBar(title = { Text("TicTacToe - ${players[model.localPlayerId.value]?.name ?: "Unknown"}") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            items(players.entries.toList()) { (documentId, player) ->
                if (documentId != model.localPlayerId.value) {
                    ListItem(
                        headlineContent = { Text("Player Name: ${player.name}") },
                        supportingContent = { Text("Status: ...") },
                        trailingContent = {
                            var hasGame = false
                            games.forEach { (gameId, game) ->
                                if (game.player1Id == model.localPlayerId.value
                                    && game.player1Id == documentId && game.gameState == "invite") {
                                    Text("Waiting for accept...")
                                    hasGame = true
                                } else if (game.player2Id == model.localPlayerId.value
                                    && game.player1Id == documentId && game.gameState == "invite") {
                                    Button(onClick = {
                                        model.db.collection("games").document(gameId)
                                            .update("gameState", "player1_turn")
                                            .addOnSuccessListener {
                                                navController.navigate("game/${gameId}")
                                            }
                                            .addOnFailureListener {
                                                Log.e("Error", "Error updating game: $gameId")
                                            }
                                    }) {
                                        Text("Accept invite")
                                    }
                                    hasGame = true
                                }
                            }
                            if (!hasGame) {
                                Button(onClick = {
                                    model.db.collection("games")
                                        .add(Game(gameState = "invite",
                                            player1Id = model.localPlayerId.value!!,
                                            player2Id = documentId))
                                }) {
                                    Text("Challenge")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(navController: NavController, model: GameModel, gameId: String?) {
    val players by model.playerMap.asStateFlow().collectAsStateWithLifecycle()
    val games by model.gameMap.asStateFlow().collectAsStateWithLifecycle()

    var playerName = "Unknown?"
    players[model.localPlayerId.value]?.let {
        playerName = it.name
    }

    if (gameId != null && games.containsKey(gameId)) {
        val game = games[gameId]!!
        Scaffold(
            topBar = { TopAppBar(title =  { Text("TicTacToe - $playerName")}) }
        ) { innerPadding ->
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(innerPadding).fillMaxWidth()
            ) {
                when (game.gameState) {
                    "player1_won", "player2_won", "draw" -> {

                        Text("Game over!", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.padding(20.dp))

                        if (game.gameState == "draw") {
                            Text("It's a Draw!", style = MaterialTheme.typography.headlineMedium)
                        } else {
                            Text(
                                "Player ${if (game.gameState == "player1_won") "1" else "2"} won!",
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                        Button(onClick = {
                            navController.navigate("lobby")
                        }) {
                            Text("Back to lobby")
                        }
                    }

                    else -> {

                        val myTurn = game.gameState == "player1_turn" && game.player1Id == model.localPlayerId.value || game.gameState == "player2_turn" && game.player2Id == model.localPlayerId.value
                        val turn = if (myTurn) "Your turn!" else "Wait for other player"
                        Text(turn, style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.padding(20.dp))

                        Text("Player 1: ${players[game.player1Id]!!.name}")
                        Text("Player 2: ${players[game.player2Id]!!.name}")
                        Text("State: ${game.gameState}")
                        Text("GameId: ${gameId}")

                        Spacer(modifier = Modifier.padding(20.dp))

                        GameBoard(
                            game = game,
                            model = model,
                            gameId = gameId,
                            myTurn = myTurn
                        )
                    }
                }


                Spacer(modifier = Modifier.padding(20.dp))

                // row * 3 + col
                // i * 3 + j

                for (i in 0 ..< rows) {
                    Row {
                        for (j in 0..< cols) {
                            Button(
                                shape = RectangleShape,
                                modifier = Modifier.size(100.dp).padding(2.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray),
                                onClick = {
                                    model.checkGameState(gameId, i * cols + j)
                                }
                            ) {
                                // Text("Cell ${i * cols + j} Value: ${game.gameBoard[i * cols + j]}")
                                if (game.gameBoard[i * cols + j] == 1) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_launcher_background),
                                        tint = Color.Red,
                                        contentDescription = "X",
                                        modifier = Modifier.size(48.dp)
                                    )
                                } else if (game.gameBoard[i * cols + j] == 2) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                        tint = Color.Blue,
                                        contentDescription = "O",
                                        modifier = Modifier.size(48.dp)
                                    )
                                } else {
                                    Text("")
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        Log.e(
            "Error",
            "Error Game not found: $gameId"
        )
        navController.navigate("lobby")
    }
}

@Composable
fun GameBoard(
    game: Game,
    model: GameModel,
    gameId: String?,
    myTurn: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Turn indicator
        Text(
            text = if (myTurn) "Your turn!" else "Wait for other player",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(8.dp),
            color = if (myTurn) Color.Green else Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Game grid
        Box(
            modifier = Modifier
                .aspectRatio(1f) // Ensure square grid
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (i in 0 until rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (j in 0 until cols) {
                            val index = i * cols + j
                            Button(
                                onClick = {
                                    if (game.gameBoard[index] == 0 && myTurn && game.gameState !in listOf("player1_won", "player2_won", "draw")) {
                                        model.checkGameState(gameId, index)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (game.gameState in listOf("player1_won", "player2_won", "draw")) Color.Gray else Color.LightGray
                                )
                            ) {
                                when (game.gameBoard[index]) {
                                    1 -> Text(
                                        text = "X",
                                        style = MaterialTheme.typography.displayLarge,
                                        color = Color.Red
                                    )
                                    2 -> Text(
                                        text = "O",
                                        style = MaterialTheme.typography.displayLarge,
                                        color = Color.Blue
                                    )
                                    else -> Text("")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun ReadyPopup(gameId: String?, game: Game, model: GameModel) {
    var showReadyPopup by remember { mutableStateOf(true) }

    if (showReadyPopup && gameId != null) {
        AlertDialog(
            onDismissRequest = { showReadyPopup = false },
            title = { Text("Ready to Start?") },
            text = { Text("Click 'Ready' to signal you're ready to start the game.") },
            confirmButton = {
                Button(onClick = {
                    val isPlayer1 = game.player1Id == model.localPlayerId.value
                    model.db.collection("games").document(gameId)
                        .update(if (isPlayer1) "player1Ready" else "player2Ready", true)
                        .addOnSuccessListener {
                            showReadyPopup = false
                        }
                }) {
                    Text("Ready")
                }
            },
            dismissButton = {
                Button(onClick = { showReadyPopup = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
