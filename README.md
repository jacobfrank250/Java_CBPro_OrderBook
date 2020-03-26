# Coinbase Pro Live Orderbook

This project maintains Coinbase Pro's live BTC•USD order book using the full websocket channel. 

The project also contains a JUnit test to verify the accuracy of the orderbook. The test periodically compares a snapshot of it's websocket-maintained orderbook, with a snapshot of the order book retrieved from the rest API

