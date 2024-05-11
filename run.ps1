$SRC_DIR = "src"
$OUT_DIR = "bin"
$MAIN_CLASS = "App"

# Build
javac -d $OUT_DIR $SRC_DIR/*.java
cp $SRC_DIR/familytree.xsd $OUT_DIR/

# Run
java -cp $OUT_DIR $MAIN_CLASS