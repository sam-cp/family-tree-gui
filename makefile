SRC_DIR = src
OUT_DIR = bin
MAIN_CLASS = App

.PHONY: all clean build run

all: build run

run:
	java -cp bin $(MAIN_CLASS)

build: $(SRC_DIR)/*.java $(SRC_DIR)/familytree.xsd
	javac -d $(OUT_DIR) $(SRC_DIR)/*.java
	cp $(SRC_DIR)/familytree.xsd $(OUT_DIR)/

clean:
	rm -f $(OUT_DIR)/*