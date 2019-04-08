package org.shmmap.common;


import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;

/**
 *  在manager重启重新加载快照之后，会生成新的数据文件，此后使用者需要切换到新的文件。
 *  所有生成的数据文件都会加上一个时间戳后缀。
 *  文件使用者打开时，会选择更新时间最新的文件打开。
 *  manager生成文件时，会使用max(当前时间戳，现有文件最大时间戳+1)生成文件，确保文件名的单调递增。
 *  这样可以保证manager新生成的文件名和之前的不重复，热加载map能够成功。
 */
public class MapFileUtils {

    public static File getLatestFile(String name) {
        Path filePath = Paths.get(name);
        Path latestPath = null;

        final PathMatcher pm = FileSystems.getDefault().getPathMatcher("regex:"+filePath.getFileName().toString()+"\\.[0-9]{10,10}");

        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(
                filePath.getParent(),  path -> pm.matches(path.getFileName()))) {
            for(Path path: dirStream) {
                if(latestPath == null || path.compareTo(latestPath) > 0) {
                    latestPath = path;
                }
            }

            if(latestPath != null) {
                return latestPath.toFile();
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static File getAvailableFile(String name) {
        Path filePath = Paths.get(name);
        final PathMatcher pm = FileSystems.getDefault().getPathMatcher("regex:"+filePath.getFileName().toString()+"\\.[0-9]{10,10}");
        ArrayList<Path> list = new ArrayList<>();

        try(DirectoryStream<Path> dirStream = Files.newDirectoryStream(
                filePath.getParent(),  path -> pm.matches(path.getFileName()))) {

            dirStream.iterator().forEachRemaining(list::add);

            //按照文件日期从近到远排序
            list.sort((o1, o2) -> {
                return -o1.getFileName().compareTo(o2.getFileName());
            });
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        Path latest = null;
        if(!list.isEmpty()) {
            latest = list.get(0);
            for(int i=list.size()-1; i > 0; i--) {
                boolean x = list.get(i).toFile().delete();
                if(!x) {
                    System.out.println("Delete file failed: " + list.get(i).toString());
                }
            }
        }

        Path ret = Paths.get(name + "." + (System.currentTimeMillis()/1000));
        int i = 0;
        if(latest != null && ret.compareTo(latest) <= 0) {
            //异常情况，使用当前最新的+1
            String[] items = latest.toString().split("\\.");
            long x = Long.parseLong(items[items.length - 1]);
            ret = Paths.get(name + "." + (x+1));
        }

        //0是异常情况的文件
        return ret.toFile();
    }
}
