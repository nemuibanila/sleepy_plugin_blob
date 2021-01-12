class CommandQueueElement(identifier:String) {
    val identifier: String = identifier
    var value: Any = Any()

    override fun equals(other: Any?): Boolean {
        if(other is CommandQueueElement) {
            return this.identifier == other.identifier && this.value.equals(other.value)
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }

    override fun toString(): String {
        return "{identifier: ${identifier}, value: ${value}}"
    }
}
