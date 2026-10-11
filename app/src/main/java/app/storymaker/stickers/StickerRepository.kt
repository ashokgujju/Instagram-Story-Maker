package app.storymaker.stickers

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Source of stickers. The editor depends only on this, so a remote source can be added later. */
interface StickerRepository {
    val stickers: Flow<List<Sticker>>

    fun find(id: String): Sticker?
}

@Singleton
class BundledStickerRepository @Inject constructor() : StickerRepository {
    private val byId = StickerCatalog.all.associateBy { it.id }

    override val stickers: Flow<List<Sticker>> = flowOf(StickerCatalog.all)

    override fun find(id: String): Sticker? = byId[id]
}

@Module
@InstallIn(SingletonComponent::class)
interface StickerModule {
    @Binds
    fun bindStickerRepository(repository: BundledStickerRepository): StickerRepository
}
