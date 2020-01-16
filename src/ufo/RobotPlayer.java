package ufo;

import battlecode.common.*;
import com.sun.tools.javac.util.Pair;

import java.lang.reflect.Type;
import java.util.ArrayList;

public strictfp class RobotPlayer {

    enum State {
        NO_STATE,
        FIND_SOUP,
        FIND_HEADQUARTER,
        BUILD_REFINERY,
        BUILD_DESIGN_SCHOOL,
        MINE_SOUP,
    }

    static class Cell{
        boolean flooded, visited;
        RobotType building;
        int elevation, soup;
    }

    static Direction[] directions = {
            Direction.SOUTHWEST,
            Direction.SOUTH,
            Direction.SOUTHEAST,
            Direction.WEST,
            Direction.CENTER,
            Direction.EAST,
            Direction.NORTHWEST,
            Direction.NORTH,
            Direction.NORTHEAST,
    };

    static RobotType[] spawnedByMiner = {RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL,
            RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN};


    static RobotController rc;

    static State state = State.NO_STATE;

    static int turnCount;

    static Cell[][] cells;

    static ArrayList< Pair<Integer, Integer> > neighbours = new ArrayList<>();

    //Helpers:

    static boolean isBuilding(RobotType robotType){
        return (robotType == RobotType.DESIGN_SCHOOL ||
            robotType == RobotType.FULFILLMENT_CENTER ||
            robotType == RobotType.VAPORATOR ||
            robotType == RobotType.REFINERY ||
            robotType == RobotType.HQ);
    }

    static void setCell(MapLocation mapLocation, Cell cell) {
        cells[mapLocation.x][mapLocation.y] = cell;
    }

    static Cell getCell(MapLocation mapLocation) {
        return cells[mapLocation.x][mapLocation.y];
    }

    static int distance(MapLocation a, MapLocation b) {
        return (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y);
    }

    private static void getNeighbours() {
        int r = rc.getType().sensorRadiusSquared;
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                if (dx * dx + dy * dy <= r)
                    neighbours.add(new Pair<>(dx, dy));
    }

    public static void run(RobotController rc) throws GameActionException {
        RobotPlayer.rc = rc;
        turnCount = 0;

        cells = new Cell[rc.getMapWidth()][rc.getMapHeight()];
        for (int i = 0; i < rc.getMapWidth(); i++)
            for (int j = 0; j < rc.getMapHeight(); j++) {
                cells[i][j] = new Cell();
            }

        try {
            getNeighbours();
        }catch(Exception e){
            System.out.println(rc.getType() + " Exception");
            e.printStackTrace();
        }


        while (true) {
            turnCount += 1;
            try {
                switch (rc.getType()) {
                    case HQ:
                        runHQ();
                        break;
                    case MINER:
                        runMiner();
                        break;
                    case REFINERY:
                        runRefinery();
                        break;
                    case VAPORATOR:
                        runVaporator();
                        break;
                    case DESIGN_SCHOOL:
                        runDesignSchool();
                        break;
                    case FULFILLMENT_CENTER:
                        runFulfillmentCenter();
                        break;
                    case LANDSCAPER:
                        runLandscaper();
                        break;
                    case DELIVERY_DRONE:
                        runDeliveryDrone();
                        break;
                    case NET_GUN:
                        runNetGun();
                        break;
                }

            } catch (Exception e) {
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();
            }
            Clock.yield();
        }
    }

    static void senseNeighbours() throws GameActionException {
        for (Pair<Integer, Integer> d : neighbours) {
            MapLocation loc = rc.getLocation().translate(d.fst, d.snd);
            if (rc.canSenseLocation(loc)){
                Cell cell = getCell(loc);
                cell.visited = true;
                cell.elevation = rc.senseElevation(loc);
                cell.flooded = rc.senseFlooding(loc);
                cell.soup = rc.senseSoup(loc);
            }
        }

        for (RobotInfo info : rc.senseNearbyRobots())
            if (info.getTeam() != rc.getTeam() && info.getTeam() != Team.NEUTRAL && isBuilding(info.getType())){
                getCell(info.getLocation()).building = info.getType();
                /*if (info.getType() == RobotType.HQ) {
                    found enemy HQ
                }*/
            }
    }

    static void runHQ() throws GameActionException {
        if (turnCount == 1)
            senseNeighbours();
    }

    static void runMiner() throws GameActionException {
    }

    static void runRefinery() throws GameActionException {
    }

    static void runVaporator() throws GameActionException {
    }

    static void runDesignSchool() throws GameActionException {
    }

    static void runFulfillmentCenter() throws GameActionException {
    }

    static void runLandscaper() throws GameActionException {
    }

    static void runDeliveryDrone() throws GameActionException {
    }

    static void runNetGun() throws GameActionException {
    }
}
