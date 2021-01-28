import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import kotlin.reflect.*

@JsModule("uuid")
@JsNonModule
external object UUID {
    fun v4(): String
}

lateinit var context2D: CanvasRenderingContext2D

fun main() {
    val c = document.getElementById("myCanvas") as HTMLCanvasElement
    context2D = c.getContext("2d") as CanvasRenderingContext2D

    val world = World()
    world.addSystem(DrawSystem, GravitySystem, MovableSystem)
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

class World {
    val entities = mutableMapOf<String, String>()
    val tags = mutableMapOf<String, String>()
    val components = mutableMapOf<String, Component>()
    val stores =
        mutableMapOf<KClass<*>, MutableMap<String, MutableList<String>>>()
    val systems = mutableListOf<System>()

    fun getEntities(): Set<String> = entities.keys

    fun createEntity(tag: String = ""): String {
        val id = UUID.v4()
        entities[id] = tag
        if (tag.isNotBlank()) tags[tag] = id
        return id
    }

    fun getId(tag: String) = tags[tag]

    @Suppress("unused")
    inline fun <reified T : Component> createComponent(crossinline init: T.() -> Unit): String {
        val id = UUID.v4()
        val clazz = T::class.js
        components[id] = js("new clazz()").unsafeCast<T>().also { init(it) }
        return id
    }

    fun addSystem(vararg systems: System) = this.systems.addAll(systems)

    inline fun <reified T : Component> getComponentStore() =
        stores.getOrPut(T::class) { mutableMapOf() }

    inline fun <reified T : Component> getEntityStore(entity: String) =
        getComponentStore<T>().getOrPut(entity) { mutableListOf() }

    inline fun <reified T : Component> addComponent(
        entity: String,
        crossinline init: T.() -> Unit = {}
    ) {
        getEntityStore<T>(entity).add(createComponent(init))
    }

    inline fun <reified T : Component> has(entity: String) =
        getEntityStore<T>(entity).size > 0

    inline fun <reified T : Component> getComponent(entity: String) =
        components[getEntityStore<T>(entity).first()] as T

    inline fun <reified T : Component> getComponents(entity: String) =
        getEntityStore<T>(entity).map { components[it] as T }

    fun update() {
        systems.forEach { it.invoke(this) }
    }
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
        set(it) {
            _ctx = it
        }
}

object GravitySensitive : Component

interface Component

interface System {
    fun World.filter(entity: String): Boolean
    fun World.logic(entity: String)
    fun invoke(world: World) = world.getEntities()
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

object Physics {
    val gravity = Vector(0.0, 0.025)
}
