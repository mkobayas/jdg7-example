var port = document.location.port;
var baseUri = document.location.hostname + (port ? ":" + port : "") + document.location.pathname;

var wsUri = "ws://" + baseUri + "ws";
var putUri = "http://" + baseUri + "example/rest/put";

var websocket = new WebSocket(wsUri);
websocket.onopen = function(evt) { onOpen(evt) };
websocket.onclose = function(evt) { onClose(evt) };
websocket.onmessage = function(evt) { onMessage(evt) };
websocket.onerror = function(evt) { onError(evt) };

var output = document.getElementById("output");
document.getElementById("put").onclick = function(evt) {put(evt)};

function onOpen() {
    writeToScreen("Connected to " + wsUri);
}

function onClose() {
    writeToScreen("Closed");
}

function onMessage(evt) {
	writeToLog(evt.data);
	var recvData = JSON.parse(evt.data);
	drawChart(recvData);
}

function onError(evt) {
    writeToScreen('<span style="color: red;">ERROR:</span> ' + evt.data);
}

function writeToScreen(message) {
    output.innerHTML += message + "<br>";
}

function writeToLog(message) {
	var time = new Date();
	var hou = time.getHours();
	var min = time.getMinutes();
	var sec = time.getSeconds();
	var timeStr = hou + ":" + min + ":" + sec;
	
	var newText;
	if(logArea.innerHTML != "") {
		newText = logArea.innerHTML + "\n" + timeStr + " " + message;
	} else {
		newText = timeStr + " " + message;
	}
	logArea.innerHTML = newText;
	logArea.scrollTop = logArea.scrollHeight;  
}

function put(message) {
	var num = records.value;
	var request = new XMLHttpRequest();
	request.open("GET", putUri + "?num=" + num);
	request.send(null);
}

var chart;
var options;
var data;

google.load("visualization", "1", {
	packages : [ "corechart" ]
});
google.setOnLoadCallback(init);

function init() {

	data = new google.visualization.DataTable();
	data.addColumn('string', '名詞');
	data.addColumn('number', '出現回数');
	data.addRows([["助詞", 0],
	              ["名詞", 0],
	              ["動詞", 0],
	              ["助動詞", 0],
	              ["補助記号", 0],
	              ["代名詞", 0],
	              ["副詞", 0],
	              ["形容詞", 0],
	              ["接尾辞", 0],
	              ["連体詞", 0],
	              ["形状詞", 0],
	              ["接続詞", 0],
	              ["接頭辞", 0],
	              ["記号", 0],
	              ["感動詞", 0]
	              ]
			);
	
	options = {
		legend : 'none',
		height : 300,
		animation: { duration: 1000, easing: 'linear'},
		hAxis: {
			minValue: 0,
			maxValue: 500000,
			gridlines: {
				count: -1
			}
		},
		vAxis: {
			textStyle: {color: 'blue', fontName: 'Meiryo', fontSize: 11}
		},
		chartArea: {width: '80%', height: '80%'}
	};
	chart = new google.visualization.BarChart(document.getElementById('chart'));
	chart.draw(data, options);

}

function drawChart(chartData) {
	for (j = 0; j < data.getNumberOfRows(); j++){
        data.setValue(j,1,chartData[j][1]);
    }
	chart.draw(data, options);
} 