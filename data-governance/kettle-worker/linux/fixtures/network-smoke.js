// Synthetic local fixture only. No XML-configurable host, port, or extra permission.
var nonce = String(java.lang.System.getProperty("governance.worker.launch.id"));
var directory = new java.io.File(String(java.lang.System.getProperty("user.dir")), "output");
function publish(name, value) {
  var target = new java.io.File(directory, name);
  var pending = new java.io.File(directory, name + ".pending");
  var writer = new java.io.OutputStreamWriter(new java.io.FileOutputStream(pending), "UTF-8");
  try { writer.write(JSON.stringify(value)); } finally { writer.close(); }
  if (!pending.renameTo(target)) throw new Error("Fixture status publication failed");
}
function waitFor(name, expected) {
  var target = new java.io.File(directory, name);
  var deadline = java.lang.System.currentTimeMillis() + 20000;
  while (java.lang.System.currentTimeMillis() < deadline) {
    if (target.isFile()) {
      var reader = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(target), "UTF-8"));
      var actual;
      try { actual = String(reader.readLine()); } finally { reader.close(); }
      if (actual == expected) return;
      throw new Error("Fixture acknowledgement identity differs");
    }
    java.lang.Thread.sleep(50);
  }
  throw new Error("Fixture acknowledgement timed out");
}
function tcpProbe(name, host, port, withAck) {
  var result = {name:name, host:host, port:port, protocol:"tcp", connected:false, acknowledged:false};
  var socket = new java.net.Socket();
  try {
    socket.connect(new java.net.InetSocketAddress(host, port), 1500);
    result.connected = true;
    if (withAck) {
      socket.setSoTimeout(1500);
      var writer = new java.io.OutputStreamWriter(socket.getOutputStream(), "UTF-8");
      writer.write("KETTLE_NETWORK_SMOKE:" + nonce + "\n"); writer.flush();
      var reader = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), "UTF-8"));
      result.acknowledged = String(reader.readLine()) == "ACK:" + nonce;
    }
  } catch (failure) { result.error = String(failure); }
  finally { socket.close(); }
  return result;
}
function udpProbe() {
  var result = {name:"dns-udp", host:"127.0.0.11", port:53, protocol:"udp", sent:false, response:false};
  var socket = new java.net.DatagramSocket();
  try {
    socket.setSoTimeout(1500);
    // Inert synthetic bytes, not a resolvable public/production DNS question.
    var bytes = new java.lang.String("KETTLE_NETWORK_SMOKE:" + nonce).getBytes("UTF-8");
    var packet = new java.net.DatagramPacket(bytes, bytes.length, java.net.InetAddress.getByName("127.0.0.11"), 53);
    socket.send(packet); result.sent = true;
    var buffer = java.lang.reflect.Array.newInstance(java.lang.Byte.TYPE, 512);
    socket.receive(new java.net.DatagramPacket(buffer, buffer.length)); result.response = true;
  } catch (failure) { result.error = String(failure); }
  finally { socket.close(); }
  return result;
}
publish("network-ready.json", {nonce:nonce, phase:"ready"});
waitFor("network-probe.go", nonce + ":GO");
var results = [tcpProbe("allowed", "172.17.0.1", 39090, true),
               tcpProbe("denied", "172.17.0.1", 39091, false),
               tcpProbe("dns-tcp", "127.0.0.11", 53, false), udpProbe()];
var processed = JSON.stringify({nonce:nonce, probes:results});
publish("network-finished.json", {nonce:nonce, phase:"finished", probes:results});
waitFor("network-observed.go", nonce + ":OBSERVED");
