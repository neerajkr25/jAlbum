package com.backend.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.backend.FileInfo;
import com.backend.facer.Face;
import com.backend.facer.FacerUtils;

public class FaceTableDao extends AbstractRecordsStore
{
    private static final Logger logger = LoggerFactory.getLogger(FaceTableDao.class);

    private static FaceTableDao instance = new FaceTableDao();

    private FaceTableDao()
    {

    }

    public static FaceTableDao getInstance()
    {
        return instance;
    }

    public void insertOneRecord(Face face)
    {
        if (face == null)
        {
            return;
        }

        lock.writeLock().lock();
        try
        {
            PreparedStatement prep = conn
                    .prepareStatement("insert into faces values(?,?,?,?,?,?,?);");
            prep.setString(1, face.getFacetoken());
            prep.setString(2, face.getEtag());
            prep.setString(3, face.getPos());
            prep.setLong(4, face.getFaceid());
            prep.setString(5, face.getQuality());
            prep.setString(6, face.getGender());
            prep.setString(7, face.getAge());
            prep.execute();
            prep.close();
            logger.info("add one face to the faces: {}", face);
        }
        catch (SQLException e)
        {
            logger.error("caught: " + face, e);
        }
        finally
        {
            lock.writeLock().unlock();
        }
    }

    public void addRecords(List<Face> faces)
    {
        if (faces == null || faces.isEmpty())
        {
            return;
        }

        for (Face f : faces)
        {
            insertOneRecord(f);
        }
    }

    public void checkAndCreateTable()
    {
        PreparedStatement prep = null;
        try
        {
            if (checkTableExist("faces"))
            {
                return;
            }

            prep = conn
                    .prepareStatement("CREATE TABLE faces (facetoken STRING, etag STRING (32, 32), "
                            + "pos STRING, faceid BIGINT, quality STRING, gender STRING, age STRING);");
            prep.execute();
            prep.close();

            prep = conn
                    .prepareStatement("CREATE INDEX faceindex ON faces (facetoken, etag, faceid);");
            prep.execute();
            prep.close();
        }
        catch (Exception e)
        {
            logger.error("caught: ", e);
        }
    }

    public boolean checkAlreadyDetect(String eTag)
    {
        if (StringUtils.isBlank(eTag))
        {
            return false;
        }

        PreparedStatement prep = null;
        ResultSet res = null;
        lock.readLock().lock();
        try
        {
            prep = conn.prepareStatement("select * from faces where etag=?;");
            prep.setString(1, eTag);
            res = prep.executeQuery();

            if (res.next())
            {
                logger.info("already exist: {}", eTag);
                return true;
            }
        }
        catch (Exception e)
        {
            logger.warn("caused by: ", e);
        }
        finally
        {
            if (prep != null)
            {
                try
                {
                    prep.close();
                }
                catch (SQLException e)
                {
                    logger.warn("caused by: ", e);
                }
            }

            if (res != null)
            {
                try
                {
                    res.close();
                }
                catch (SQLException e)
                {
                    logger.warn("caused by: ", e);
                }
            }
            lock.readLock().unlock();
        }
        return false;
    }

    public void addEmptyRecords(FileInfo fi)
    {
        Face f = new Face();
        f.setEtag(fi.getHash256());
        f.setFaceid(-1);
        f.setPos("null");
        f.setFacetoken("null");
        f.setAge("null");
        f.setGender("null");
        f.setQuality("0");
        insertOneRecord(f);
    }

    public List<Face> getAllNewFaces()
    {
        PreparedStatement prep = null;
        ResultSet res = null;
        lock.readLock().lock();
        try
        {
            prep = conn.prepareStatement("select * from faces where faceid=? "
                    + "and facetoken <> ? order by quality asc;");
            prep.setLong(1, -1);
            prep.setString(2, "null");
            res = prep.executeQuery();

            List<Face> flst = new LinkedList<Face>();
            while (res.next())
            {
                flst.add(getFaceFromTableRecord(res));
            }

            prep.close();
            res.close();

            return flst;
        }
        catch (Exception e)
        {
            logger.warn("caused by: ", e);
        }
        finally
        {
            lock.readLock().unlock();
        }

        return null;
    }

    public Face getFace(String token)
    {
        if (StringUtils.isBlank(token))
        {
            return null;
        }

        PreparedStatement prep = null;
        ResultSet res = null;
        lock.readLock().lock();
        try
        {
            prep = conn.prepareStatement("select * from faces where facetoken=?;");
            prep.setString(1, token);
            res = prep.executeQuery();

            Face f = null;
            if (res.next())
            {
                f = getFaceFromTableRecord(res);
            }

            prep.close();
            res.close();

            return f;
        }
        catch (Exception e)
        {
            logger.warn("caused by: ", e);
        }
        finally
        {
            lock.readLock().unlock();
        }

        return null;
    }

    private Face getFaceFromTableRecord(ResultSet res) throws SQLException
    {
        /*
         * CREATE TABLE faces (facetoken STRING, etag STRING (32, 32), pos
         * STRING, faceid BIGINT, quality STRING, gender STRING, age STRING);
         * 
         */
        Face f = new Face();
        f.setEtag(res.getString("etag"));
        f.setFacetoken(res.getString("facetoken"));
        f.setPos(res.getString("pos"));
        f.setFaceid(res.getLong("faceid"));
        f.setGender(res.getString("gender"));
        f.setAge(res.getString("age"));
        f.setQuality(res.getString("quality"));
        logger.debug("get a face record: {}", f);
        return f;
    }

    public void updateFaceID(Face f)
    {
        if (f == null)
        {
            return;
        }

        PreparedStatement prep = null;
        lock.writeLock().lock();
        try
        {
            prep = conn.prepareStatement("update faces set faceid=? where facetoken=?;");
            prep.setLong(1, f.getFaceid());
            prep.setString(2, f.getFacetoken());
            prep.execute();
        }
        catch (Exception e)
        {
            logger.warn("caused by: ", e);
        }
        finally
        {
            lock.writeLock().unlock();
        }
    }

    public void deleteOneFile(String f)
    {
        if (f == null)
        {
            return;
        }

        PreparedStatement prep = null;
        lock.writeLock().lock();
        try
        {
            prep = conn.prepareStatement("delete from faces where etag=?;");
            prep.setString(1, f);
            prep.execute();
        }
        catch (Exception e)
        {
            logger.warn("caused by: ", e);
        }
        finally
        {
            lock.writeLock().unlock();
        }
    }

    public void updateFaceID(List<Face> flst)
    {
        if (flst == null || flst.isEmpty())
        {
            return;
        }

        for (Face f : flst)
        {
            updateFaceID(f);
        }
    }

    public List<Long> getAllValidFaceID(int facecount)
    {
        List<Long> lst = new ArrayList<Long>();
        PreparedStatement prep = null;
        ResultSet res = null;
        lock.readLock().lock();
        try
        {
            prep = conn.prepareStatement(
                    "select faceid,count(faceid) " + "from faces where faceid!='-1' "
                            + "group by faceid order by count(faceid) desc limit "
                            + ((facecount > 0 && facecount < 300) ? facecount : 25) + ";");
            res = prep.executeQuery();

            while (res.next())
            {
                lst.add(res.getLong(1));
            }

            prep.close();
            res.close();

            return lst;
        }
        catch (Exception e)
        {
            logger.warn("caused by: ", e);
        }
        finally
        {
            lock.readLock().unlock();
        }
        return lst;
    }

    public List<Face> getFacesByID(long id)
    {
        if (id < 0)
        {
            return null;
        }

        List<Face> lst = new ArrayList<Face>();
        PreparedStatement prep = null;
        ResultSet res = null;
        lock.readLock().lock();
        try
        {
            prep = conn.prepareStatement("select * from faces where faceid=?;");
            prep.setLong(1, id);
            res = prep.executeQuery();

            while (res.next())
            {
                lst.add(getFaceFromTableRecord(res));
            }

            prep.close();
            res.close();

            return lst;
        }
        catch (Exception e)
        {
            logger.warn("caused by: ", e);
        }
        finally
        {
            lock.readLock().unlock();
        }

        return lst;
    }

    public void updateFaceID(long oldfaceid, long newfaceid)
    {
        if (oldfaceid == -1 || newfaceid == -1)
        {
            return;
        }

        PreparedStatement prep = null;
        lock.writeLock().lock();
        try
        {
            prep = conn.prepareStatement("update faces set faceid=? where faceid=?;");
            prep.setLong(1, newfaceid);
            prep.setLong(2, oldfaceid);
            prep.execute();
        }
        catch (Exception e)
        {
            logger.warn("caused by: ", e);
        }
        finally
        {
            lock.writeLock().unlock();
        }
    }

    public List<Face> getNextNineFileByHashStr(String id, int count, boolean isnext)
    {
        PreparedStatement prep = null;
        ResultSet res = null;

        try
        {
            lock.readLock().lock();
            Face f = null;
            if (id != null)
            {
                f = getFace(id);
            }

            if (f == null)
            {
                return null;
            }

            long faceID = f.getFaceid();
            if (faceID == -1)
            {
                return null;
            }

            /*
             * String sqlstr = "select * from faces where faceid=? and quality"
             * + (isnext ? "<" : ">") + "=? order by quality desc limit " +
             * count; prep = conn.prepareStatement(sqlstr); prep.setLong(1,
             * faceID); prep.setString(2, f.getQuality()); res =
             * prep.executeQuery();
             * 
             * List<Face> flst = new LinkedList<Face>(); while (res.next()) {
             * flst.add(getFaceFromTableRecord(res)); }
             */

            List<Face> allf = getFacesByID(faceID);
            FacerUtils.sortByQuality(allf);
            if (!isnext)
            {
                Collections.reverse(allf);
            }

            int maxCount = allf.size() < count ? allf.size() : count;

            int c = 0;
            List<Face> flst = new LinkedList<Face>();
            boolean start = false;
            for (Face face : allf)
            {
                if (!start && face.getFacetoken().equals(f.getFacetoken()))
                {
                    start = true;
                    continue;
                }

                if (start)
                {
                    flst.add(face);
                    c++;
                    if (c >= maxCount)
                    {
                        break;
                    }
                }
            }

            return flst;
        }
        catch (Exception e)
        {
            logger.error("caught: ", e);
        }
        finally
        {
            try
            {
                if (prep != null)
                {
                    prep.close();
                }
            }
            catch (SQLException e)
            {
                logger.warn("caused by: ", e);
            }
            try
            {
                if (res != null)
                {
                    res.close();
                }
            }
            catch (SQLException e)
            {
                logger.warn("caused by: ", e);
            }
            lock.readLock().unlock();
        }

        return null;
    }

}