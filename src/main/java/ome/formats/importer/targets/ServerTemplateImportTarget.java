/*
 *  Copyright (C) 2015-2019 University of Dundee. All rights reserved.
 *
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *------------------------------------------------------------------------------
 */

package ome.formats.importer.targets;

import static omero.rtypes.rstring;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ome.formats.OMEROMetadataStoreClient;
import ome.formats.importer.ImportContainer;
import omero.api.IQueryPrx;
import omero.api.IUpdatePrx;
import omero.model.Dataset;
import omero.model.DatasetI;
import omero.model.IObject;
import omero.model.Screen;
import omero.model.ScreenI;
import omero.sys.Parameters;
import omero.sys.ParametersI;

import org.apache.commons.lang.StringUtils;

public class ServerTemplateImportTarget extends TemplateImportTarget {

    private final String sharedPath;

    public ServerTemplateImportTarget(String sharedPath) {
        this.sharedPath = sharedPath;
    }

    @Override
    public IObject load(OMEROMetadataStoreClient client, ImportContainer ic) throws Exception {
        return load(client, ic.getIsSPW());
    }

    public IObject load(OMEROMetadataStoreClient client, boolean spw) throws Exception {
        log.info("Checking '{}' against '{}'", sharedPath, getTemplate());
        Pattern pattern = Pattern.compile(getTemplate());
        Matcher m = pattern.matcher(sharedPath);
        if (!m.matches()) {
            log.warn("No match");
            return null;
        }

        if (!getDiscriminator().matches("^[-+%@]?name$")) {
            log.warn("Invalid discriminator: {}", getDiscriminator());
            return null;
        }

        String name = m.group("Container1");
        if (StringUtils.isBlank(name)) {
            log.warn("Empty name");
            return null;
        }

        final List<IObject> objs;
        if (getDiscriminator().startsWith("@")) {
            objs = Collections.emptyList();
        } else {
            final IQueryPrx query = client.getServiceFactory().getQueryService();
            final StringBuilder hql = new StringBuilder();
            hql.append("FROM ");
            hql.append(spw ? "Screen" : "Dataset");
            hql.append(" WHERE name = :name ORDER BY id");
            if (!getDiscriminator().startsWith("-")) {
                hql.append(" DESC");
            }
            final Parameters params = new ParametersI().add("name", rstring(name));
            objs = query.findAllByQuery(hql.toString(), params);
            final Iterator<IObject> objIter = objs.iterator();
            while (objIter.hasNext()) {
                if (!objIter.next().getDetails().getPermissions().canLink()) {
                    objIter.remove();
                }
            }
        }
        if (objs.isEmpty()) {
            final IUpdatePrx update = client.getServiceFactory().getUpdateService();
            if (spw) {
                final Screen screen = new ScreenI();
                screen.setName(rstring(name));
                return update.saveAndReturnObject(screen);
            } else {
                final Dataset dataset = new DatasetI();
                dataset.setName(rstring(name));
                return update.saveAndReturnObject(dataset);
            }
        } else if (getDiscriminator().startsWith("%") && objs.size() > 1) {
            log.warn("No unique {} called {}", spw ? "screen" : "dataset", name);
            return null;
        } else {
            return objs.get(0);
        }
    }
}
