package ufo;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Random;

public strictfp class RobotPlayer {
    static Random rand;

    enum State {
        NO_STATE,
        MINER_EXPLORE,
        MINER_RETURN, MINER_SOUP
    }

    static class Cell {
        boolean flooded, visited;
        RobotType robot;
        int elevation, soup;
        int last_ignore = -1000;
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
    static final int shift = 10;
    static MapLocation[] neighbours = new MapLocation[200];
    static int sizeNeighbours;
    static ArrayList<MapLocation> history = new ArrayList<>();
    static ArrayList<MapLocation> preferred = new ArrayList<>();

    //Helpers:

    static void setCell(MapLocation mapLocation, Cell cell) {
        cells[mapLocation.x][mapLocation.y] = cell;
    }

    static Cell getCell(MapLocation mapLocation) {
        return cells[mapLocation.x + shift][mapLocation.y + shift];
    }

    static int distance(MapLocation a, MapLocation b) {
        return (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y);
    }

    private static void getNeighbours() {
        int r = (int) Math.sqrt(rc.getType().sensorRadiusSquared) + 2;
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                if (dx * dx + dy * dy <= rc.getType().sensorRadiusSquared)
                    neighbours[sizeNeighbours++] = new MapLocation(dx, dy);
    }

    public static void run(RobotController rc) throws GameActionException {
        rand = new Random(rc.getID());
        RobotPlayer.rc = rc;
        turnCount = 0;

//        System.out.println("begin init");

        cells = new Cell[rc.getMapWidth() + shift * 2][rc.getMapHeight() + shift * 2];
        for (int i = shift; i < rc.getMapWidth() + shift; i++)
            for (int j = shift; j < rc.getMapHeight() + shift; j++) {
                cells[i][j] = new Cell();
            }

//        System.out.println("end init");

        try {
            /*if (rc.getID() == 11611)
                System.out.println("I started scan");*/
            getNeighbours();
            /*if (rc.getID() == 11611)
                System.out.println("I finished scan");*/
        } catch (Exception e) {
            System.out.println(rc.getType() + " Exception");
            e.printStackTrace();
        }

        while (true) {
            /*if (rc.getID() == 11611)
                System.out.println("I'm 11611");*/
            turnCount += 1;
            try {
                senseNeighbours();
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
        for (int i = 0; i < sizeNeighbours; i++) {
            MapLocation d = neighbours[i];
            MapLocation loc = rc.getLocation().translate(d.x, d.y);
            if (rc.canSenseLocation(loc)) {
                Cell cell = getCell(loc);
                cell.visited = true;
//                System.out.println(loc+" "+rc.canSenseLocation(loc));
                cell.elevation = rc.senseElevation(loc);
                cell.flooded = rc.senseFlooding(loc);
                cell.soup = rc.senseSoup(loc);
            }
        }
        RobotInfo[] nearbyInfo = rc.senseNearbyRobots();
        for (int i = 0; i < nearbyInfo.length; i++) {
            RobotInfo info = nearbyInfo[i];
            getCell(info.getLocation()).robot = info.getType();
                /*if (info.getType() == RobotType.HQ) {
                    found enemy HQ
                }*/
        }
    }

    static int numberOfMiners = 0;
    static final int soupSaveTurns = 4;
    static int lastTurnSoup[] = new int[soupSaveTurns];

    static void runHQ() throws GameActionException {
        int differenceSoup = rc.getTeamSoup() - lastTurnSoup[0];
        if (numberOfMiners < 10 || differenceSoup > RobotType.MINER.cost * 3 / 2)
            for (Direction direction : directions)
                if (rc.canBuildRobot(RobotType.MINER, direction)) {
                    rc.buildRobot(RobotType.MINER, direction);
                    numberOfMiners++;
                }
        for (int i = 0; i < soupSaveTurns - 1; i++)
            lastTurnSoup[i] = lastTurnSoup[i + 1];
        lastTurnSoup[soupSaveTurns - 1] = rc.getTeamSoup();
    }

    static boolean backToHome = false;

    static void runMiner() throws GameActionException {
        if (turnCount == 1) {
            state = State.MINER_EXPLORE;
            minerInitExplore();
        }
        if (state == State.MINER_EXPLORE && rc.isReady()) {
            MapLocation soup = getASoup();
            if (soup != null) {
                minerDestination = soup;
                if (rc.canMineSoup(rc.getLocation().directionTo(soup))) {
                    okToUsePreferred = false;
                    preferred.clear();
                    state = State.MINER_SOUP;
                    backToHome = false;
                }
            }
            if (rc.getSoupCarrying() > 0 && soup == null || rc.getSoupCarrying() == RobotType.MINER.soupLimit) {
                for (Direction dir : directions)
                    if (rc.canDepositSoup(dir)) {
                        rc.depositSoup(dir, rc.getSoupCarrying());
                        okToUsePreferred = true;
                        backToHome = false;
                        history.clear();
                        history.add(rc.getLocation());
                        return;
                    }

                RobotInfo[] robots = rc.senseNearbyRobots();
                MapLocation refinery = null;
                for (RobotInfo robot : robots)
                    if (robot.type == RobotType.REFINERY && robot.team == rc.getTeam())
                        refinery = robot.getLocation();

                MapLocation[] soups = rc.senseNearbySoup();
                int soupSum = 0;
                for (MapLocation soupTmp : soups)
                    soupSum += rc.senseSoup(soupTmp);

                if (refinery == null && rc.getTeamSoup() > 750 && soupSum > 500) {
                    for (Direction direction : directions)
                        if (rc.canBuildRobot(RobotType.REFINERY, direction)) {
                            rc.buildRobot(RobotType.REFINERY, direction);
                            return;
                        }
                }

                if (refinery == null) {
                    backToHome = true;
                    MapLocation pathToHQ = history.get(history.size() - 1);
                    if (getCell(pathToHQ).last_ignore > turnCount) {
                        if (history.size() != 1) {
                            for (int i = 0; i < 5 && history.size() >= 2; i++)
                                history.remove(history.size() - 1);
                            pathToHQ = history.get(history.size() - 1);
                            preferred.add(pathToHQ);
                        } else pathToHQ = minerDestination;
                    }
                    minerDestination = pathToHQ;
                } else minerDestination = refinery;
            }
            if (state == State.MINER_EXPLORE && rc.isReady()) {
                Direction dir = moveTowards(minerDestination);
                if (dir == Direction.CENTER) {
                    getCell(minerDestination).last_ignore = turnCount + (int) Math.sqrt(rc.getType().sensorRadiusSquared * (rc.getCooldownTurns() + 1)) + 4;
                    minerInitExplore();
                } else {
                    if (!backToHome)
                        history.add(rc.getLocation());
                    rc.move(dir);
                }
            }
        }

        if (state == State.MINER_SOUP && rc.isReady()) {
            Direction dir = rc.getLocation().directionTo(minerDestination);
            if (rc.canMineSoup(dir))
                rc.mineSoup(dir);
            else
                state = State.MINER_EXPLORE;
        }
    }

    private static Direction moveTowards(MapLocation dest) {
        Direction bestDir = Direction.CENTER;

        for (Direction dir : directions) {
            MapLocation nextLoc = rc.adjacentLocation(dir);
            if (nextLoc.distanceSquaredTo(dest)
                    < rc.adjacentLocation(bestDir).distanceSquaredTo(dest)
                    && rc.canMove(dir)
                    && !getCell(nextLoc).flooded) {
                bestDir = dir;
            }
        }

        return bestDir;
    }

    private static MapLocation getASoup() {
        MapLocation ret = null;
        MapLocation soups[] = rc.senseNearbySoup();
        for (int i = 0; i < soups.length; i++) {
            MapLocation loc = soups[i];
            Cell cell = getCell(loc);
            if (cell.last_ignore < turnCount && (ret == null
                    || ret.distanceSquaredTo(rc.getLocation()) > loc.distanceSquaredTo(rc.getLocation())))
                ret = loc;
        }

        return ret;
    }

    static MapLocation minerDestination;
    static boolean okToUsePreferred = false;

    private static void minerInitExplore() {
        if (okToUsePreferred && preferred.size() > 0) {
            minerDestination = preferred.get(preferred.size() - 1);
            preferred.remove(preferred.size() - 1);
            System.out.println("az in ja raftama");
        } else minerDestination = new MapLocation(rand.nextInt(rc.getMapWidth()), rand.nextInt(rc.getMapHeight()));
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
