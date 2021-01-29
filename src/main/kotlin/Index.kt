import WorldOperations.addComponent
import WorldOperations.getComponent
import WorldOperations.has
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import kotlin.reflect.KClass

@JsModule("uuid")
@JsNonModule
external object UUID {
    fun v4(): String
}

lateinit var context2D: CanvasRenderingContext2D

@Suppress("MagicNumber")
fun main() {
    val c = document.getElementById("myCanvas") as HTMLCanvasElement
    context2D = c.getContext("2d") as CanvasRenderingContext2D

    val world = ConcreteWorld(DrawSystem, GravitySystem, MovableSystem)
    val first = world.createEntity()
    world.addComponent<Transform>(first) {
        position = Vector(10.0, 10.0)
        size = Vector(10.0, 10.0)
    }
    world.addComponent<Drawable>(first) {
        ctx = context2D
    }
    world.addComponent<Velocity>(first) {
        velocity = Vector(0.25, 0.0)
    }

    val second = world.createEntity()
    world.addComponent<Transform>(second) {
        position = Vector(20.0, 20.0)
        size = Vector(10.0, 10.0)
    }
    world.addComponent<Drawable>(second) {
        ctx = context2D
    }
    world.addComponent<Velocity>(second) {
        velocity = Vector(0.25, 0.0)
    }
    world.addComponent<GravitySensitive>(second)

    val frameRate = 1 / 60
    val width = context2D.canvas.width.toDouble()
    val height = context2D.canvas.height.toDouble()
    window.setInterval({
        context2D.clearRect(0.0, 0.0, width, height)
        world.update()
    }, frameRate)
}

interface World {
    val entities: MutableMap<String, String>
    val tags: MutableMap<String, String>
    val components: MutableMap<String, Component>
    val stores: MutableMap<KClass<*>, MutableMap<String, MutableList<String>>>
    val systems: MutableList<System>

    fun createEntity(tag: String = ""): String {
        val id = UUID.v4()
        entities[id] = tag
        if (tag.isNotBlank()) tags[tag] = id
        return id
    }

    fun getId(tag: String): String =
        tags[tag] ?: ""

    fun update() {
        systems.forEach { it.invoke(this) }
    }
}

object WorldOperations {
    inline fun <reified T : Component> World.createComponent(
        crossinline init: T.() -> Unit
    ): String = T::class.js.let {
        val component = js("new it()").unsafeCast<T>()
        init(component)
        UUID.v4().also { components[it] = component }
    }

    inline fun <reified T : Component> World.getComponentStore() =
        stores.getOrPut(T::class) { mutableMapOf() }

    inline fun <reified T : Component> World.getEntityStore(entity: String) =
        getComponentStore<T>().getOrPut(entity) { mutableListOf() }

    inline fun <reified T : Component> World.addComponent(
        entity: String,
        crossinline init: T.() -> Unit = {}
    ) {
        getEntityStore<T>(entity).add(createComponent(init))
    }

    inline fun <reified T : Component> World.has(entity: String) =
        getEntityStore<T>(entity).size > 0

    inline fun <reified T : Component> World.getComponent(entity: String) =
        components[getEntityStore<T>(entity).first()] as T
}

class ConcreteWorld(vararg systems: System) : World {
    override val entities = mutableMapOf<String, String>()
    override val tags = mutableMapOf<String, String>()
    override val components = mutableMapOf<String, Component>()
    override val stores = mutableMapOf<KClass<*>, MutableMap<String, MutableList<String>>>()
    override val systems = mutableListOf<System>().apply { plusAssign(systems.asList()) }
}

class Vector(
    var x: Double = 1.0,
    var y: Double = 1.0
)

class Transform(
    var position: Vector = Vector(),
    var size: Vector = Vector()
) : Component

class Velocity(
    var velocity: Vector = Vector()
) : Component

class Drawable : Component {
    private lateinit var _ctx: CanvasRenderingContext2D
    var ctx: CanvasRenderingContext2D
        get() = _ctx
        set(it) { _ctx = it }
}

object GravitySensitive : Component

interface Component

interface System {
    fun World.filter(entity: String): Boolean
    fun World.logic(entity: String)
    fun invoke(world: World) = world.entities.keys
        .filter { world.filter(it) }
        .forEach { world.logic(it) }
}

object DrawSystem : System {
    override fun World.filter(entity: String) =
        has<Drawable>(entity) && has<Transform>(entity)

    override fun World.logic(entity: String) {
        val draw = getComponent<Drawable>(entity)
        val transform = getComponent<Transform>(entity)
        draw.ctx.fillRect(
            transform.position.x,
            transform.position.y,
            transform.size.x,
            transform.size.y
        )
    }
}

object MovableSystem : System {
    override fun World.filter(entity: String) =
        has<Velocity>(entity) && has<Transform>(entity)

    override fun World.logic(entity: String) {
        val velocity = getComponent<Velocity>(entity)
        val transform = getComponent<Transform>(entity)
        transform.position.x += velocity.velocity.x
        transform.position.y += velocity.velocity.y
    }
}

object GravitySystem : System {
    override fun World.filter(entity: String) =
        has<Velocity>(entity) && has<GravitySensitive>(entity)

    override fun World.logic(entity: String) {
        val velocity = getComponent<Velocity>(entity)
        velocity.velocity.x += Physics.gravity.x
        velocity.velocity.y += Physics.gravity.y
    }
}

@Suppress("MagicNumber")
object Physics {
    val gravity = Vector(0.0, 0.025)
}
