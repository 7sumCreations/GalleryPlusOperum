package com.android.gallery3d.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.Menu;
import android.view.MenuItem;

import com.android.gallery3d.R;
import com.android.gallery3d.data.MediaObject;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * PhotoPage feeds the viewed item's support bits to
 * MenuExecutor.updateMenuOperation(). A trashed photo reports
 * SUPPORT_INFO | SUPPORT_RESTORE | SUPPORT_DELETE_FOREVER, so the viewer opened
 * from Trash must offer Restore and Delete permanently and must not offer the
 * ordinary Delete (which would only re-trash it); a normal photo the reverse.
 */
public class TrashViewerMenuTest {

    private static final int TRASHED = MediaObject.SUPPORT_INFO
            | MediaObject.SUPPORT_RESTORE | MediaObject.SUPPORT_DELETE_FOREVER;
    private static final int NORMAL_IMAGE = MediaObject.SUPPORT_DELETE
            | MediaObject.SUPPORT_ROTATE | MediaObject.SUPPORT_SHARE
            | MediaObject.SUPPORT_CROP | MediaObject.SUPPORT_INFO
            | MediaObject.SUPPORT_MOVE | MediaObject.SUPPORT_COPY
            | MediaObject.SUPPORT_FAVOURITE;

    private Map<Integer, Boolean> visible;
    private Menu menu;

    @Before
    public void setUp() {
        visible = new HashMap<Integer, Boolean>();
        menu = fakeMenu(visible, R.id.action_delete, R.id.action_restore,
                R.id.action_delete_forever, R.id.action_move, R.id.action_favourite,
                R.id.action_share);
    }

    @Test
    public void aTrashedItemOffersRestoreAndDeleteForeverOnly() {
        MenuExecutor.updateMenuOperation(menu, TRASHED);

        assertTrue(visible.get(R.id.action_restore));
        assertTrue(visible.get(R.id.action_delete_forever));
        assertFalse("Delete would only re-trash it", visible.get(R.id.action_delete));
        assertFalse(visible.get(R.id.action_move));
        assertFalse(visible.get(R.id.action_favourite));
        assertFalse(visible.get(R.id.action_share));
    }

    @Test
    public void aNormalItemDoesNotOfferTheTrashActions() {
        MenuExecutor.updateMenuOperation(menu, NORMAL_IMAGE);

        assertFalse(visible.get(R.id.action_restore));
        assertFalse(visible.get(R.id.action_delete_forever));
        assertTrue(visible.get(R.id.action_delete));
        assertTrue(visible.get(R.id.action_move));
    }

    /** A Menu holding only the given ids, recording each item's visibility. */
    private static Menu fakeMenu(final Map<Integer, Boolean> visible, int... ids) {
        final Map<Integer, MenuItem> items = new HashMap<Integer, MenuItem>();
        for (final int id : ids) {
            visible.put(id, Boolean.FALSE);
            items.put(id, (MenuItem) Proxy.newProxyInstance(
                    MenuItem.class.getClassLoader(), new Class<?>[] {MenuItem.class},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) {
                            if (method.getName().equals("setVisible")) {
                                visible.put(id, (Boolean) args[0]);
                                return proxy;
                            }
                            if (method.getName().equals("getItemId")) return id;
                            return defaultFor(method.getReturnType());
                        }
                    }));
        }
        return (Menu) Proxy.newProxyInstance(Menu.class.getClassLoader(),
                new Class<?>[] {Menu.class}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if (method.getName().equals("findItem")) {
                            return items.get((Integer) args[0]);
                        }
                        return defaultFor(method.getReturnType());
                    }
                });
    }

    private static Object defaultFor(Class<?> type) {
        if (type == boolean.class) return Boolean.FALSE;
        if (type == int.class) return 0;
        return null;
    }
}
