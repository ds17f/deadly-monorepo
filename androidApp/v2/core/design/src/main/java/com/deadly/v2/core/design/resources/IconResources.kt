package com.deadly.v2.core.design.resources

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import com.deadly.v2.core.design.R

/**
 * Centralized resource for all icons used in the V2 Deadly application.
 * 
 * This object provides consistent access to Material Icons and custom icons,
 * ensuring consistent icon usage throughout the V2 application.
 * 
 * Usage pattern:
 * - All icons are accessed via @Composable functions that return a Painter
 * - This provides a uniform API whether the icon comes from a drawable or Material Icons
 */
object IconResources {
    /**
     * Navigation icons used in the application.
     */
    object Navigation {
        @Composable
        fun Home() = customIcon(R.drawable.ic_home)
        
        @Composable
        fun HomeOutlined() = vectorIcon(Icons.Outlined.Home)
        
        @Composable
        fun Search() = vectorIcon(Icons.Filled.Search)
        
        @Composable
        fun SearchOutlined() = vectorIcon(Icons.Outlined.Search)
        
        @Composable
        fun Library() = customIcon(R.drawable.ic_library_add_check)
        
        @Composable
        fun LibraryOutlined() = customIcon(R.drawable.ic_library_add)
        
        @Composable
        fun Settings() = customIcon(R.drawable.ic_settings)
        
        @Composable
        fun SettingsOutlined() = vectorIcon(Icons.Outlined.Settings)
        
        @Composable
        fun Add() = vectorIcon(Icons.Filled.Add)
        
        @Composable
        fun Back() = customIcon(R.drawable.ic_arrow_back)
        
        @Composable
        fun Forward() = customIcon(R.drawable.ic_arrow_forward)
        
        @Composable
        fun Close() = customIcon(R.drawable.ic_close)
        
        @Composable
        fun MoreVertical() = customIcon(R.drawable.ic_more_vert)
        
        @Composable
        fun KeyboardArrowDown() = customIcon(R.drawable.ic_keyboard_arrow_down)
        
        @Composable
        fun KeyboardArrowUp() = customIcon(R.drawable.ic_keyboard_arrow_up)
        
        @Composable
        fun KeyboardArrowLeft() = customIcon(R.drawable.ic_keyboard_arrow_left)
        
        @Composable
        fun KeyboardArrowRight() = customIcon(R.drawable.ic_keyboard_arrow_right)
        
        @Composable
        fun ChevronLeft() = customIcon(R.drawable.ic_chevron_left)
        
        @Composable
        fun ChevronRight() = customIcon(R.drawable.ic_chevron_right)
        
        @Composable
        fun ExpandMore() = customIcon(R.drawable.ic_expand_more)
        
        @Composable
        fun ExpandLess() = customIcon(R.drawable.ic_expand_less)
        
        @Composable
        fun Fullscreen() = customIcon(R.drawable.ic_fullscreen)
        
        @Composable
        fun FullscreenExit() = customIcon(R.drawable.ic_fullscreen_exit)
        
        @Composable
        fun Menu() = customIcon(R.drawable.ic_menu)
        
        @Composable
        fun SwapVert() = customIcon(R.drawable.ic_swap_vert)
        
        @Composable
        fun Collections() = customIcon(R.drawable.ic_collections)
    }
    
    // Player control icons are accessed via the PlayerControls object
    /**
     * Player control icons used for media playback.
     */
    object PlayerControls {
        @Composable
        fun Play() = customIcon(R.drawable.ic_play_arrow)
        
        @Composable
        fun Pause() = customIcon(R.drawable.ic_pause)
        
        @Composable
        fun SkipNext() = customIcon(R.drawable.ic_skip_next)
        
        @Composable
        fun SkipPrevious() = customIcon(R.drawable.ic_skip_previous)
        
        @Composable
        fun FastForward() = customIcon(R.drawable.ic_fast_forward)
        
        @Composable
        fun FastRewind() = customIcon(R.drawable.ic_fast_rewind)
        
        @Composable
        fun AlbumArt() = customIcon(R.drawable.ic_album)
        
        @Composable
        fun Queue() = customIcon(R.drawable.ic_queue_music)
        
        @Composable
        fun Repeat() = customIcon(R.drawable.ic_repeat)
        
        @Composable
        fun RepeatOne() = customIcon(R.drawable.ic_repeat_one)
        
        @Composable
        fun Shuffle() = customIcon(R.drawable.ic_shuffle)
        
        @Composable
        fun VolumeUp() = customIcon(R.drawable.ic_volume_up)
        
        @Composable
        fun VolumeDown() = customIcon(R.drawable.ic_volume_down)
        
        @Composable
        fun VolumeMute() = customIcon(R.drawable.ic_volume_off)
        
        @Composable
        fun VolumeMute2() = customIcon(R.drawable.ic_volume_mute)
        
        @Composable
        fun MusicNote() = customIcon(R.drawable.ic_music_note)
        
        @Composable
        fun PlayCircleFilled() = customIcon(R.drawable.ic_play_circle_filled)
        
        @Composable
        fun PauseCircleFilled() = customIcon(R.drawable.ic_pause_circle_filled)
    }
    
    // Status icons are in the Status object category
    /**
     * Status and notification icons.
     */
    object Status {
        @Composable
        fun CheckCircle() = customIcon(R.drawable.ic_check_circle)
        
        @Composable
        fun Warning() = customIcon(R.drawable.ic_warning)
        
        @Composable
        fun Info() = customIcon(R.drawable.ic_info)
        
        @Composable
        fun Error() = customIcon(R.drawable.ic_error)
        
        @Composable
        fun Done() = customIcon(R.drawable.ic_done)
        
        @Composable
        fun DoneAll() = customIcon(R.drawable.ic_done_all)
        
        @Composable
        fun Sync() = customIcon(R.drawable.ic_sync)
        
        @Composable
        fun SyncProblem() = customIcon(R.drawable.ic_sync_problem)
        
        @Composable
        fun Refresh() = customIcon(R.drawable.ic_refresh)
        
        // For compatibility
        @Composable
        fun Success() = CheckCircle()
    }
    
    // Content type icons are accessed via the Content object
    /**
     * Content type icons.
     */
    object Content {
        @Composable
        fun GetApp() = customIcon(R.drawable.ic_get_app)
        
        @Composable
        fun CloudDownload() = customIcon(R.drawable.ic_cloud_download)
        
        @Composable
        fun FileDownload() = customIcon(R.drawable.ic_file_download)
        
        @Composable
        fun DownloadDone() = customIcon(R.drawable.ic_download_done)
        
        @Composable
        fun DownloadForOffline() = customIcon(R.drawable.ic_download_for_offline)
        
        @Composable
        fun FilePresent() = customIcon(R.drawable.ic_file_present)
        
        @Composable
        fun FileCopy() = customIcon(R.drawable.ic_file_copy)
        
        @Composable
        fun Folder() = customIcon(R.drawable.ic_folder)
        
        @Composable
        fun FolderOpen() = customIcon(R.drawable.ic_folder_open)
        
        @Composable
        fun LibraryMusic() = customIcon(R.drawable.ic_library_music)
        
        @Composable
        fun LibraryAdd() = customIcon(R.drawable.ic_library_add)
        
        @Composable
        fun LibraryAddCheck() = customIcon(R.drawable.ic_library_add_check)
        
        @Composable
        fun Queue() = customIcon(R.drawable.ic_queue)
        
        @Composable
        fun Star() = customIcon(R.drawable.ic_star)
        
        @Composable
        fun StarBorder() = customIcon(R.drawable.ic_star_border)
        
        @Composable
        fun StarHalf() = customIcon(R.drawable.ic_star_half)
        
        @Composable
        fun Favorite() = customIcon(R.drawable.ic_favorite)
        
        @Composable
        fun FavoriteBorder() = customIcon(R.drawable.ic_favorite_border)
        
        @Composable
        fun Search() = customIcon(R.drawable.ic_search)
        
        @Composable
        fun PlaylistAdd() = customIcon(R.drawable.ic_playlist_add)
        
        @Composable
        fun PlaylistAddCheck() = customIcon(R.drawable.ic_playlist_add_check)
        
        @Composable
        fun PlaylistPlay() = customIcon(R.drawable.ic_playlist_play)
        
        @Composable
        fun AddCircle() = customIcon(R.drawable.ic_add_circle)
        
        @Composable
        fun AddCircleOutline() = customIcon(R.drawable.ic_add_circle_outline)
        
        @Composable
        fun Share() = customIcon(R.drawable.ic_share)
        
        @Composable
        fun Cast() = customIcon(R.drawable.ic_cast)
        
        @Composable
        fun TrendingUp() = customIcon(R.drawable.ic_trending_up)
        
        @Composable
        fun FormatListBulleted() = customIcon(R.drawable.ic_format_list_bulleted)
        
        @Composable
        fun GridView() = customIcon(R.drawable.ic_grid_view)
        
        @Composable
        fun Remove() = customIcon(R.drawable.ic_remove)
        
        @Composable
        fun Delete() = customIcon(R.drawable.ic_delete)
        
        @Composable
        fun PushPin() = customIcon(R.drawable.ic_push_pin)
        
        @Composable
        fun QrCode() = customIcon(R.drawable.ic_qr_code)
        
        @Composable
        fun QrCodeScanner() = customIcon(R.drawable.ic_qr_code_scanner)
        
        @Composable
        fun ArrowCircleDown() = customIcon(R.drawable.ic_arrow_circle_down)
        
    }

    /**
     * Data management icons for backup, restore, save operations.
     */
    object DataManagement {
        @Composable
        fun Save() = customIcon(R.drawable.ic_save)
        
        @Composable
        fun Restore() = customIcon(R.drawable.ic_restore)
        
        @Composable
        fun Backup() = customIcon(R.drawable.ic_backup)
        
        @Composable
        fun SettingsBackupRestore() = customIcon(R.drawable.ic_settings_backup_restore)
    }

    /**
     * Get a custom icon from drawable resources.
     *
     * @param resId The resource ID of the drawable
     * @return A Painter for the resource
     */
    @Composable
    fun customIcon(@DrawableRes resId: Int): Painter {
        return painterResource(id = resId)
    }
    
    /**
     * Get a Material Icons ImageVector as a Composable function.
     * This allows us to provide a consistent API for all icons.
     *
     * @param imageVector The Material Icons ImageVector
     * @return A Composable function that renders the ImageVector
     */
    @Composable
    fun vectorIcon(imageVector: ImageVector): Painter {
        return androidx.compose.ui.graphics.vector.rememberVectorPainter(image = imageVector)
    }

    /**
     * Icon size definitions for consistent sizing throughout the app.
     */
    object Size {
        val SMALL = 16.dp
        val MEDIUM = 24.dp
        val LARGE = 32.dp
        val XLARGE = 48.dp
    }
}