	var socket = new WebSocket(location.origin.replace(/^http/, 'ws') + '/lab');
	socket.binaryType = 'arraybuffer';
	var log = console.log;
	var map;
	var lab1markers = new Map();
	var lab2markers = new Map();

	var colors = ['#ff0000', '#ffff00', '#00ff00'];
	
	/* window.onload = function() {
		//Add your Unwired Maps Access Token here (not the API token!)
		//var unwired;
		unwired.key = mapboxgl.accessToken = 'xyz';
		//Define the map and configure the map's theme
		map = new mapboxgl.Map({
			container: 'map',
			attributionControl: false, //need this to show a compact attribution icon (i) instead of the whole text
			style: unwired.getLayer("streets"), //get Unwired's style template
			zoom: 7,
			center: [-1.3402795, 52.0601807] 	// Banbury
		});
	}*/
	
	socket.onopen = function() {
		log('Opened connection');
	  
	};

	socket.onerror = function (event) {
	  log('Error: ' + JSON.stringify(event));
	};

	window.addEventListener("beforeunload", function(e){
	  socket.close();
	  log('Closed connection');
	}, false);

	socket.onmessage = function (event) {
		
		var message = readPacket( event );
		//log('Received Message: ' + message);

		var fields = message.split(',');

		if (fields == '') //  received message is empty
			return;			
		
		var messageType = fields[0];
		if (messageType == 'm0') { // Config Map token
			//Add your Unwired Maps Access Token here (not the API token!)
			unwired.key = mapboxgl.accessToken = fields[1];
			//Define the map and configure the map's theme
			map = new mapboxgl.Map({
				container: 'map',
				attributionControl: false, //need this to show a compact attribution icon (i) instead of the whole text
				style: unwired.getLayer("streets"), //get Unwired's style template
				zoom: 6,
				center: [-1.510477, 52.4081812] 	// Coventry
	//			center: [-0.213736,51.523524]		// London
			});
		}			
		else if (messageType == 'm1') { //LAB 1 message received
			var keyMarker = fields[1] + fields[2];

			if (lab1markers.has(keyMarker)){
				// change color if the marker already exists
				lab1markers.get(keyMarker).style.backgroundColor = colors[fields[3]];
			} else {
				// create a DOM element for the marker
				var el = document.createElement('div');
				el.className = 'circle';
				el.style.backgroundColor = colors[fields[3] - 1];

				lab1markers.set(keyMarker,el);

				// add marker to map
				new mapboxgl.Marker(el)
					.setLngLat([fields[2],fields[1]])
					.addTo(map);
			}
		} 
		else if (messageType == 'm2') { //LAB 2 message received

			// create a DOM element for the marker
			var el = document.createElement('div');
			el.className = 'markerVehicle';
			
			el.addEventListener('click', function() {
				window.alert(fields[1]);
			});

			// Remove the marker if it already exists
			if (lab2markers.has(fields[1]))
				lab2markers.get(fields[1]).remove();

			// add marker to map
			var marker = new mapboxgl.Marker(el)
				.setLngLat([fields[3], fields[2]])
				.addTo(map);
			
			// store the marker by vehicle id
			lab2markers.set(fields[1], marker);
		} 
	};
	
	var readPacket = function(event){
		return String.fromCharCode.apply(null, new Uint8Array(event.data));
		// reading protobuf
//		var reader = protobuf.Reader.create(new Uint8Array(event.data));
//		while (reader.pos < reader.len) {
//			var tag = reader.uint32();
//			switch (/*id*/ tag >>> 3) {
//				case 1:
//					return reader.string();
//					break;
//				default:
//					reader.skipType(/*wireType*/ tag & 7);
//					break;
//			}
//		}
	}	


