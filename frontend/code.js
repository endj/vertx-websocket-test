const mount = document.getElementById("container")
const input = addNewElement("input");
input.placeholder = "Username"
input.onkeydown = e => onEnter(e, createUser)
input.focus()

const userSocket = new Map();
const chats = []
const sockets = []

const onEnter = (e, fn) => {
  if (e.key === "Enter" || e.keyCode === 13) fn(e.target.value, e.target);
}

function createUser(userName, target) {
  if (!userName) return;
  target.value = ""
  if (userSocket.has(userName)) return

  const sock = createSocket();
  userSocket.set(userName, sock)
  sock.onopen = () => {
    register(sock, userName)
  }

  const onSendMsg = (msg, target) => {
    send(sock, userName, msg)
    target.value = ""
  }



  const windowElement = createChatWindow(userName, onSendMsg, sock);

  mount.appendChild(windowElement)
}



const createChatWindow = (userId, onMsg, socket) => {
  const container = document.createElement("div");
  container.classList.add("chatWindow")

  const chatTextList = document.createElement("div")
  container.appendChild(chatTextList);

  const textInput = document.createElement("input")
  textInput.placeholder = userId + ": Message..."
  const sendContainer = document.createElement("div")
  sendContainer.classList.add("chatInput")
  sendContainer.appendChild(textInput)

  textInput.onkeydown = e => {
    onEnter(e, onMsg)
  };

  socket.onmessage = event => {
    const text = document.createElement("div")

    const message = JSON.parse(event.data)
    if (message.sender === userId) {
      text.classList.add("sender")
    }

    text.textContent = `${message.sender}: ${message.text}`;
    chatTextList.appendChild(text)
    text.style.color = getColorFromString(message.sender)

    text.scrollIntoView()
  }

  setInterval(() => heartBeat(socket, userId), 60_000)

  container.appendChild(sendContainer)
  return container;
}


function send(socket, sender, message) {
  console.log(sender, "sending:", message)
  socket.send(JSON.stringify({
    type: "message",
    data: {
      sender: sender,
      text: message
    }
  }))
}

function register(socket, userId) {
  socket.send(JSON.stringify({
    type: "register",
    data: {
      id: userId
    }
  }))
}

function heartBeat(socket, userId) {
  socket.send(JSON.stringify({
    type: "heartBeat",
    data: {
      id: userId
    }
  }))
}

const createSocket = (onMsgReceived) => {
  const socket = new WebSocket("ws://localhost:8080/websocket");
  socket.onmessage = event => onMsgReceived(event.data)
  socket.onerror = event => console.error("WebSocket error:", event);
  socket.onclose = event => console.log("WebSocket closed:", event);
  return socket;
}



function addNewElement(type) {
  const ul = document.createElement(type)
  document.body.appendChild(ul)
  return ul;
}




function getColorFromString(str) {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = str.charCodeAt(i) + ((hash << 5) - hash);
  }
  const hue = hash % 360;
  const saturation = 70;
  const lightness = 40;
  return `hsl(${hue}, ${saturation}%, ${lightness}%)`;
}