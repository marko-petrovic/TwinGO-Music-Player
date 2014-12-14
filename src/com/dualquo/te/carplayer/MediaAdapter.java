/*
 * Copyright (C) 2010, 2011 Christopher Eby <kreed@kreed.org>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.dualquo.te.carplayer;

import java.util.regex.Pattern;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.SectionIndexer;

import com.dualquo.te.carplayer.R;

/**
 * MediaAdapter provides an adapter backed by a MediaStore content provider.
 * It generates simple one- or two-line text views to display each media
 * element.
 *
 * Filtering is supported, as is a more specific type of filtering referred to
 * as limiting. Limiting is separate from filtering; a new filter will not
 * erase an active filter. Limiting is intended to allow only media belonging
 * to a specific group to be displayed, e.g. only songs from a certain artist.
 * See getLimiter and setLimiter for details.
 */
public class MediaAdapter extends CursorAdapter implements SectionIndexer, LibraryAdapter {
	private static final Pattern SPACE_SPLIT = Pattern.compile("\\s+");

	/**
	 * A context to use.
	 */
	private final Context mContext;
	/**
	 * The type of media represented by this adapter. Must be one of the
	 * MediaUtils.FIELD_* constants. Determines which content provider to query for
	 * media and what fields to display.
	 */
	private final int mType;
	/**
	 * The URI of the content provider backing this adapter.
	 */
	private Uri mStore;
	/**
	 * The fields to use from the content provider. The last field will be
	 * displayed in the MediaView, as will the first field if there are
	 * multiple fields. Other fields will be used for searching.
	 */
	private String[] mFields;
	/**
	 * The collation keys corresponding to each field. If provided, these are
	 * used to speed up sorting and filtering.
	 */
	private String[] mFieldKeys;
	/**
	 * The columns to query from the content provider.
	 */
	private String[] mProjection;
	/**
	 * If true, show an expand arrow next the the text in each view.
	 */
	private final boolean mExpandable;
	/**
	 * A limiter is used for filtering. The intention is to restrict items
	 * displayed in the list to only those of a specific artist or album, as
	 * selected through an expander arrow in a broader MediaAdapter list.
	 */
	private Limiter mLimiter;
	/**
	 * The constraint used for filtering, set by the search box.
	 */
	private String mConstraint;
	/**
	 * The section indexer, for the letter pop-up when scrolling.
	 */
	private final MusicAlphabetIndexer mIndexer;
	/**
	 * True if this adapter should have a special MediaView with custom text in
	 * the first row.
	 */
	private final boolean mHasHeader;
	/**
	 * The text to show in the header.
	 */
	private String mHeaderText;
	/**
	 * The sort order for use with buildSongQuery().
	 */
	private String mSongSort;
	/**
	 * The human-readable descriptions for each sort mode.
	 */
	private int[] mSortEntries;
	/**
	 * An array ORDER BY expressions for each sort mode. %1$s is replaced by
	 * ASC or DESC as appropriate before being passed to the query.
	 */
	private String[] mSortValues;
	/**
	 * The index of the current of the current sort mode in mSortValues, or
	 * the inverse of the index (in which case sort should be descending
	 * instead of ascending).
	 */
	private int mSortMode;

	/**
	 * Construct a MediaAdapter representing the given <code>type</code> of
	 * media.
	 *
	 * @param context A context to use.
	 * @param type The type of media to represent. Must be one of the
	 * Song.TYPE_* constants. This determines which content provider to query
	 * and what fields to display in the views.
	 * @param expandable Whether an expand arrow should be shown to the right
	 * of the views' text
	 * @param hasHeader Whether this view has a header row.
	 * @param limiter An initial limiter to use
	 */
	public MediaAdapter(Context context, int type, boolean expandable, boolean hasHeader, Limiter limiter)
	{
		super(context, null, false);

		mContext = context;
		mType = type;
		mExpandable = expandable;
		mHasHeader = hasHeader;
		mLimiter = limiter;
		mIndexer = new MusicAlphabetIndexer(1);

		switch (type) {
		case MediaUtils.TYPE_ARTIST:
			mStore = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;
			mFields = new String[] { MediaStore.Audio.Artists.ARTIST };
			mFieldKeys = new String[] { MediaStore.Audio.Artists.ARTIST_KEY };
			mSongSort = MediaUtils.DEFAULT_SORT;
			mSortEntries = new int[] { R.string.name, R.string.number_of_tracks };
			mSortValues = new String[] { "artist_key %1$s", "number_of_tracks %1$s,artist_key %1$s" };
			break;
		case MediaUtils.TYPE_ALBUM:
			mStore = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
			mFields = new String[] { MediaStore.Audio.Albums.ARTIST, MediaStore.Audio.Albums.ALBUM };
			// Why is there no artist_key column constant in the album MediaStore? The column does seem to exist.
			mFieldKeys = new String[] { "artist_key", MediaStore.Audio.Albums.ALBUM_KEY };
			mSongSort = "album_key,track";
			mSortEntries = new int[] { R.string.name, R.string.artist_album, R.string.year, R.string.number_of_tracks };
			mSortValues = new String[] { "album_key %1$s", "artist_key %1$s,album_key %1$s", "minyear %1$s,album_key %1$s", "numsongs %1$s,album_key %1$s" };
			break;
		case MediaUtils.TYPE_SONG:
			mStore = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
			mFields = new String[] { MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.TITLE };
			mFieldKeys = new String[] { MediaStore.Audio.Media.ARTIST_KEY, MediaStore.Audio.Media.ALBUM_KEY, MediaStore.Audio.Media.TITLE_KEY };
			mSortEntries = new int[] { R.string.name, R.string.artist_album_track, R.string.artist_album_title, R.string.artist_year, R.string.year };
			mSortValues = new String[] { "title_key %1$s", "artist_key %1$s,album_key %1$s,track %1$s", "artist_key %1$s,album_key %1$s,title_key %1$s", "artist_key %1$s,year %1$s,track %1$s", "year %1$s,title_key %1$s" };
			break;
		case MediaUtils.TYPE_PLAYLIST:
			mStore = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI;
			mFields = new String[] { MediaStore.Audio.Playlists.NAME };
			mFieldKeys = null;
			mSortEntries = new int[] { R.string.name, R.string.date_added };
			mSortValues = new String[] { "name %1$s", "date_added %1$s" };
			break;
		case MediaUtils.TYPE_GENRE:
			mStore = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI;
			mFields = new String[] { MediaStore.Audio.Genres.NAME };
			mFieldKeys = null;
			mSortEntries = new int[] { R.string.name };
			mSortValues = new String[] { "name %1$s" };
			break;
		default:
			throw new IllegalArgumentException("Invalid value for type: " + type);
		}

		if (mFields.length == 1)
			mProjection = new String[] { BaseColumns._ID, mFields[0] };
		else
			mProjection = new String[] { BaseColumns._ID, mFields[mFields.length - 1], mFields[0] };
	}

	@Override
	public int getCount()
	{
		int count = super.getCount();
		if (count == 0)
			return 0;
		else if (mHasHeader)
			return count + 1;
		else
			return count;
	}

	@Override
	public Object getItem(int pos)
	{
		if (mHasHeader) {
			if (pos == 0)
				return null;
			else
				pos -= 1;
		}

		return super.getItem(pos);
	}

	@Override
	public long getItemId(int pos)
	{
		if (mHasHeader) {
			if (pos == 0)
				return -1;
			else
				pos -= 1;
		}

		return super.getItemId(pos);
	}

	@Override
	public View getView(int pos, View convertView, ViewGroup parent)
	{
		if (mHasHeader) {
			if (pos == 0) {
				MediaView view;
				if (convertView == null)
					view = new MediaView(mContext, null, mExpandable ? MediaView.sExpander : null);
				else
					view = (MediaView)convertView;
				view.makeHeader(mHeaderText);
				return view;
			} else {
				pos -= 1;
			}
		}

		return super.getView(pos, convertView, parent);
	}

	/**
	 * Modify the header text to be shown in the first row.
	 *
	 * @param text The new text.
	 */
	public void setHeaderText(String text)
	{
		mHeaderText = text;
		notifyDataSetChanged();
	}

	@Override
	public void setFilter(String filter)
	{
		mConstraint = filter;
	}

	/**
	 * Build the query to be run with runQuery().
	 *
	 * @param projection The columns to query.
	 * @param forceMusicCheck Force the is_music check to be added to the
	 * selection.
	 */
	public QueryTask buildQuery(String[] projection, boolean forceMusicCheck)
	{
		String constraint = mConstraint;
		Limiter limiter = mLimiter;

		StringBuilder selection = new StringBuilder();
		String[] selectionArgs = null;

		int mode = mSortMode;
		String sortDir;
		if (mode < 0) {
			mode = ~mode;
			sortDir = "DESC";
		} else {
			sortDir = "ASC";
		}
		String sort = String.format(mSortValues[mode], sortDir);

		if (mType == MediaUtils.TYPE_SONG || forceMusicCheck)
			selection.append("is_music!=0");

		if (constraint != null && constraint.length() != 0) {
			String[] needles;
			String[] keySource;

			// If we are using sorting keys, we need to change our constraint
			// into a list of collation keys. Otherwise, just split the
			// constraint with no modification.
			if (mFieldKeys != null) {
				String colKey = MediaStore.Audio.keyFor(constraint);
				String spaceColKey = DatabaseUtils.getCollationKey(" ");
				needles = colKey.split(spaceColKey);
				keySource = mFieldKeys;
			} else {
				needles = SPACE_SPLIT.split(constraint);
				keySource = mFields;
			}

			int size = needles.length;
			selectionArgs = new String[size];

			StringBuilder keys = new StringBuilder(20);
			keys.append(keySource[0]);
			for (int j = 1; j != keySource.length; ++j) {
				keys.append("||");
				keys.append(keySource[j]);
			}

			for (int j = 0; j != needles.length; ++j) {
				selectionArgs[j] = '%' + needles[j] + '%';

				// If we have something in the selection args (i.e. j > 0), we
				// must have something in the selection, so we can skip the more
				// costly direct check of the selection length.
				if (j != 0 || selection.length() != 0)
					selection.append(" AND ");
				selection.append(keys);
				selection.append(" LIKE ?");
			}
		}

		if (limiter != null && limiter.type == MediaUtils.TYPE_GENRE) {
			// Genre is not standard metadata for MediaStore.Audio.Media.
			// We have to query it through a separate provider. : /
			return MediaUtils.buildGenreQuery((Long)limiter.data, projection,  selection.toString(), selectionArgs, sort);
		} else {
			if (limiter != null) {
				if (selection.length() != 0)
					selection.append(" AND ");
				selection.append(limiter.data);
			}

			return new QueryTask(mStore, projection, selection.toString(), selectionArgs, sort);
		}
	}

	@Override
	public Object query()
	{
		return buildQuery(mProjection, false).runQuery(mContext.getContentResolver());
	}

	@Override
	public void commitQuery(Object data)
	{
		changeCursor((Cursor)data);
	}

	/**
	 * Build a query for all the songs represented by this adapter, for adding
	 * to the timeline.
	 *
	 * @param projection The columns to query.
	 */
	public QueryTask buildSongQuery(String[] projection)
	{
		QueryTask query = buildQuery(projection, true);
		if (mType != MediaUtils.TYPE_SONG) {
			query.setUri(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
			// Would be better to match the sort order in the adapter. This
			// is likely to require significantly more work though.
			query.setSortOrder(mSongSort);
		}
		return query;
	}

	@Override
	public void clear()
	{
		changeCursor(null);
	}

	@Override
	public int getMediaType()
	{
		return mType;
	}

	@Override
	public void setLimiter(Limiter limiter)
	{
		mLimiter = limiter;
	}

	@Override
	public Limiter getLimiter()
	{
		return mLimiter;
	}

	@Override
	public Limiter buildLimiter(long id)
	{
		String[] fields;
		Object data;

		Cursor cursor = getCursor();
		if (cursor == null)
			return null;
		for (int i = 0, count = cursor.getCount(); i != count; ++i) {
			cursor.moveToPosition(i);
			if (cursor.getLong(0) == id)
				break;
		}

		switch (mType) {
		case MediaUtils.TYPE_ARTIST:
			fields = new String[] { cursor.getString(1) };
			data = String.format("%s=%d", MediaStore.Audio.Media.ARTIST_ID, id);
			break;
		case MediaUtils.TYPE_ALBUM:
			fields = new String[] { cursor.getString(2), cursor.getString(1) };
			data = String.format("%s=%d",  MediaStore.Audio.Media.ALBUM_ID, id);
			break;
		case MediaUtils.TYPE_GENRE:
			fields = new String[] { cursor.getString(1) };
			data = id;
			break;
		default:
			throw new IllegalStateException("getLimiter() is not supported for media type: " + mType);
		}

		return new Limiter(mType, fields, data);
	}

	@Override
	public void changeCursor(Cursor cursor)
	{
		super.changeCursor(cursor);
		mIndexer.setCursor(cursor);
	}

	@Override
	public Object[] getSections()
	{
		if (mSortMode != 0)
			return null;
		return mIndexer.getSections();
	}

	@Override
	public int getPositionForSection(int section)
	{
		int offset = 0;
		if (mHasHeader) {
			if (section == 0)
				return 0;
			offset = 1;
		}
		return offset + mIndexer.getPositionForSection(section);
	}

	@Override
	public int getSectionForPosition(int position)
	{
		// never called by FastScroller
		return 0;
	}

	/**
	 * Update the values in the given view.
	 */
	@Override
	public void bindView(View view, Context context, Cursor cursor)
	{
		((MediaView)view).updateMedia(cursor, mFields.length > 1);
	}

	/**
	 * Generate a new view.
	 */
	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent)
	{
		return new MediaView(mContext, null, mExpandable ? MediaView.sExpander : null);
	}

	/**
	 * Returns the type of the current limiter.
	 *
	 * @return One of MediaUtils.TYPE_, or MediaUtils.TYPE_INVALID if there is
	 * no limiter set.
	 */
	public int getLimiterType()
	{
		Limiter limiter = mLimiter;
		if (limiter != null)
			return limiter.type;
		return MediaUtils.TYPE_INVALID;
	}

	/**
	 * Return the available sort modes for this adapter.
	 *
	 * @return An array containing the resource ids of the sort mode strings.
	 */
	public int[] getSortEntries()
	{
		return mSortEntries;
	}

	/**
	 * Set the sorting mode. The adapter should be re-queried after changing
	 * this.
	 *
	 * @param i The index of the sort mode in the sort entries array. If this
	 * is negative, the inverse of the index will be used and sort order will
	 * be reversed.
	 */
	public void setSortMode(int i)
	{
		mSortMode = i;
	}

	/**
	 * Returns the sort mode that should be used if no preference is saved. This
	 * may very based on the active limiter.
	 */
	public int getDefaultSortMode()
	{
		Limiter limiter = mLimiter;
		if (limiter != null && limiter.type == MediaUtils.TYPE_ALBUM)
			return 1; // artist,album,track
		return 0;
	}

	/**
	 * Return the current sort mode set on this adapter.
	 */
	public int getSortMode()
	{
		return mSortMode;
	}
}
